package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationRepository extends JpaRepository<Annotation, UUID> {

    Optional<Annotation> findByItemResultId(UUID itemResultId);

    List<Annotation> findByItemResultIdIn(Collection<UUID> itemResultIds);

    void deleteByItemResultId(UUID itemResultId);
}
