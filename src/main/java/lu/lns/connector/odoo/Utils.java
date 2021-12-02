package lu.lns.connector.odoo;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Utils {

    private Utils() {
        // no instancing
    }

    /**
     * Transforms the given stream to contain only distinct objects regarding a key that is determined
     * by a function applied on the stream objects. This method is not included in Java stream API for now.
     *
     * @param stream       original stream of objects
     * @param keyExtractor to determine a key that is used for distinct checking
     * @param <T>          stream object types
     * @param <R>          type of key
     * @return transformed stream containing only distinct objects regarding the keys
     */
    public static <T, R> Stream<T> distinctBy(Stream<T> stream, Function<T, R> keyExtractor) {
        return stream.map(obj -> new DistinctByItem<>(keyExtractor.apply(obj), obj))
                .distinct()
                .map(item -> item.object);
    }

    private static class DistinctByItem<T, R> {

        private R key;
        private T object;

        public DistinctByItem(R key, T object) {
            this.key = key;
            this.object = object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DistinctByItem<?, ?> that = (DistinctByItem<?, ?>) o;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }

    }

}
