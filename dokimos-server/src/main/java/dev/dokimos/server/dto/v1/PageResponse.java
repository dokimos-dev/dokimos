package dev.dokimos.server.dto.v1;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Stable page envelope used by paginated endpoints. The JSON shape mirrors Spring's default
 * {@code Page<T>} serialization field-for-field (including the nested {@code sort} and
 * {@code pageable} objects) so a single generated client type can deserialize responses from any
 * paginated endpoint regardless of whether the controller returns {@code Page<T>} directly or wraps
 * it in this DTO.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements,
        boolean empty,
        PageSort sort,
        PageablePage pageable) {

    public static <T> PageResponse<T> of(Page<T> page) {
        Sort sortOrder = page.getSort();
        Pageable pageable = page.getPageable();
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements(),
                page.isEmpty(),
                PageSort.of(sortOrder),
                PageablePage.of(pageable));
    }

    /** Mirrors Spring's {@code SortObject} JSON shape. */
    public record PageSort(boolean empty, boolean sorted, boolean unsorted) {
        public static PageSort of(Sort sort) {
            boolean sorted = sort != null && sort.isSorted();
            return new PageSort(!sorted, sorted, !sorted);
        }
    }

    /** Mirrors Spring's {@code PageableObject} JSON shape. */
    public record PageablePage(
            long offset, PageSort sort, boolean paged, int pageNumber, int pageSize, boolean unpaged) {
        public static PageablePage of(Pageable pageable) {
            if (pageable == null || pageable.isUnpaged()) {
                return new PageablePage(0L, PageSort.of(Sort.unsorted()), false, 0, 0, true);
            }
            return new PageablePage(
                    pageable.getOffset(),
                    PageSort.of(pageable.getSort()),
                    pageable.isPaged(),
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    pageable.isUnpaged());
        }
    }
}
