package ai.ultimate.memory;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for Memory entity.
 * Converts Memory → MemoryResponse for API output.
 *
 * MapStruct generates the implementation at compile time.
 * No manual mapping code needed.
 */
@Mapper(componentModel =
        MappingConstants.ComponentModel.SPRING)
public interface MemoryMapper {

    /**
     * Convert Memory entity to API response.
     * All matching field names map automatically.
     * No @Mapping annotations needed (names match).
     */
    MemoryResponse toResponse(Memory memory);
}