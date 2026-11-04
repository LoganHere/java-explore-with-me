package ru.practicum.statsclient;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StatsClient {

    public static final String APP_NAME = "ewm-main-service";

    private static final DateTimeFormatter STATS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;

    private final String baseUrl;

    public StatsClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.setObjectMapper(buildObjectMapper()));
    }

    private ObjectMapper buildObjectMapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(STATS_DATE_TIME));
        module.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public EndpointHit saveHit(String uri, String ip) {
        EndpointHit hit = EndpointHit.builder()
                .app(APP_NAME)
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build();
        try {
            return restTemplate.postForEntity(baseUrl + "/hit", hit, EndpointHit.class).getBody();
        } catch (RestClientException exception) {
            return null;
        }
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        List<String> uriParams = uris == null ? List.of() : uris.stream().filter(Objects::nonNull).toList();
        URI url = buildStatsUrl(start, end, uriParams, unique);
        try {
            ViewStats[] response = restTemplate.getForObject(url, ViewStats[].class);
            return response == null ? List.of() : new ArrayList<>(Arrays.asList(response));
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private URI buildStatsUrl(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        StringBuilder query = new StringBuilder()
                .append("start=").append(encode(start.format(STATS_DATE_TIME)))
                .append("&end=").append(encode(end.format(STATS_DATE_TIME)));
        for (String uri : uris) {
            query.append("&uris=").append(encode(uri));
        }
        query.append("&unique=").append(unique);
        return URI.create(baseUrl + "/stats?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter[] FORMATS = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value = parser.getValueAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            for (DateTimeFormatter format : FORMATS) {
                try {
                    return LocalDateTime.parse(value.trim(), format);
                } catch (RuntimeException exception) {
                    // пробуем следующий формат
                }
            }
            return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value,
                    "Неподдерживаемый формат даты и времени");
        }
    }
}
