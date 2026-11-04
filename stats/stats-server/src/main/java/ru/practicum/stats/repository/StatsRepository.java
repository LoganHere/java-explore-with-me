package ru.practicum.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stats.entity.EndpointHitEntity;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

public interface StatsRepository extends JpaRepository<EndpointHitEntity, Long> {

    @Query("select new ru.practicum.statsdto.ViewStats(s.app, s.uri, count(s.ip)) " +
            "from EndpointHitEntity s " +
            "where s.timestamp between :start and :end " +
            "group by s.app, s.uri " +
            "order by count(s.ip) desc")
    List<ViewStats> findAllStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select new ru.practicum.statsdto.ViewStats(s.app, s.uri, count(distinct s.ip)) " +
            "from EndpointHitEntity s " +
            "where s.timestamp between :start and :end " +
            "group by s.app, s.uri " +
            "order by count(distinct s.ip) desc")
    List<ViewStats> findAllUniqueStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select new ru.practicum.statsdto.ViewStats(s.app, s.uri, count(s.ip)) " +
            "from EndpointHitEntity s " +
            "where s.timestamp between :start and :end and s.uri in :uris " +
            "group by s.app, s.uri " +
            "order by count(s.ip) desc")
    List<ViewStats> findStatsByUris(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("uris") List<String> uris);

    @Query("select new ru.practicum.statsdto.ViewStats(s.app, s.uri, count(distinct s.ip)) " +
            "from EndpointHitEntity s " +
            "where s.timestamp between :start and :end and s.uri in :uris " +
            "group by s.app, s.uri " +
            "order by count(distinct s.ip) desc")
    List<ViewStats> findUniqueStatsByUris(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("uris") List<String> uris);
}
