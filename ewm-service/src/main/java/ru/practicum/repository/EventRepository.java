package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.entity.Event;
import ru.practicum.entity.EventState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e " +
            "where e.state = ru.practicum.entity.EventState.PUBLISHED " +
            "and (cast(:text as string) is null or lower(e.annotation) like lower(concat('%', cast(:text as string), '%')) " +
            "or lower(e.description) like lower(concat('%', cast(:text as string), '%'))) " +
            "and (cast(:categories as string) is null or e.category.id in :categories) " +
            "and (cast(:paid as boolean) is null or e.paid = :paid) " +
            "and e.eventDate >= :rangeStart " +
            "and (cast(:rangeEnd as timestamp) is null or e.eventDate <= :rangeEnd) " +
            "and (:onlyAvailable = false or e.participantLimit = 0 or " +
            "e.participantLimit > (select count(r) from ParticipationRequest r " +
            "where r.event.id = e.id and r.status = ru.practicum.entity.RequestStatus.CONFIRMED))")
    Page<Event> searchPublishedEvents(@Param("text") String text,
                                     @Param("categories") List<Long> categories,
                                     @Param("paid") Boolean paid,
                                     @Param("rangeStart") LocalDateTime rangeStart,
                                     @Param("rangeEnd") LocalDateTime rangeEnd,
                                     @Param("onlyAvailable") boolean onlyAvailable,
                                     Pageable pageable);

    @Query("select e from Event e " +
            "where (cast(:users as string) is null or e.initiator.id in :users) " +
            "and (cast(:states as string) is null or e.state in :states) " +
            "and (cast(:categories as string) is null or e.category.id in :categories) " +
            "and (cast(:rangeStart as timestamp) is null or e.eventDate >= :rangeStart) " +
            "and (cast(:rangeEnd as timestamp) is null or e.eventDate <= :rangeEnd)")
    Page<Event> searchEventsForAdmin(@Param("users") List<Long> users,
                                     @Param("states") List<EventState> states,
                                     @Param("categories") List<Long> categories,
                                     @Param("rangeStart") LocalDateTime rangeStart,
                                     @Param("rangeEnd") LocalDateTime rangeEnd,
                                     Pageable pageable);

    Page<Event> findByInitiatorId(Long initiatorId, Pageable pageable);

    @Query("select e from Event e left join fetch e.category left join fetch e.initiator where e.id = :id")
    Optional<Event> findByIdWithCategoryAndInitiator(@Param("id") Long id);

    long countByCategoryId(Long categoryId);
}
