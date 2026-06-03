package dev.dokimos.core;

import com.fasterxml.jackson.databind.JavaType;
import dev.dokimos.core.internal.Json;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A super-type token that captures a full generic type {@code T} (including its type arguments) at
 * compile time so it survives erasure at runtime.
 *
 * <p>This is the "Gafter gadget" pattern (also used by Spring's {@code ParameterizedTypeReference}
 * and Jackson's {@code TypeReference}). It lets the typed output accessors convert a stored value
 * into a generic target such as {@code List<Whisky>}, which a plain {@code Class<T>} cannot express.
 *
 * <p>Always instantiate it as an anonymous subclass so the type argument is recorded in the class's
 * generic supertype:
 *
 * <pre>{@code
 * OutputType<List<Whisky>> type = new OutputType<>() {};
 * List<Whisky> whiskies = testCase.actualOutputAs(type);
 * }</pre>
 *
 * <p>For a non-generic target a plain {@code Class<T>} accessor is simpler and preferred.
 *
 * <p>This type is Jackson-free on its public surface by design; the Jackson {@link JavaType} it
 * resolves to is an internal implementation detail.
 *
 * @param <T> the captured output type
 */
public abstract class OutputType<T> {

    private final Type type;

    /**
     * Captures the generic type argument {@code T} from the concrete (usually anonymous) subclass.
     *
     * @throws IllegalArgumentException if this token is constructed without an actual type argument
     *     (i.e. used raw, as {@code new OutputType() {}} rather than {@code new OutputType<List<X>>()
     *     {}})
     */
    protected OutputType() {
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException(
                    "OutputType must be created with an actual type argument, e.g."
                            + " new OutputType<List<Whisky>>() {} — it was constructed raw.");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    /**
     * The captured generic type. Exposed so callers can introspect the token; conversion goes
     * through the internal resolver.
     *
     * @return the captured {@link Type}
     */
    public final Type getType() {
        return type;
    }

    /**
     * Resolves this token to the Jackson {@link JavaType} used internally for value conversion. Not
     * part of the public API contract — package-private on purpose to keep Jackson off the public
     * surface.
     *
     * @return the resolved Jackson type
     */
    final JavaType toJavaType() {
        return Json.resolveType(type);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof OutputType<?> other && type.equals(other.type);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(type);
    }

    @Override
    public final String toString() {
        return "OutputType<" + type.getTypeName() + ">";
    }
}
