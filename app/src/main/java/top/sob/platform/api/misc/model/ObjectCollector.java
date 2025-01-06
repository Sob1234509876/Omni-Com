package top.sob.platform.api.misc.model;

public interface ObjectCollector<T, R> {

    T getProvided();

    Class<? extends T> provided();

    R getReturned();

    void ret(R ret);

    Class<? extends R> returned();

    static <T, R> ObjectCollector<T, R> create(T provided, Class<? extends R> rCls) {
        return new ObjectCollector<T, R>() {

            private R ret;

            @Override
            public T getProvided() {
                return provided;
            }

            @SuppressWarnings("unchecked")
            @Override
            public Class<? extends T> provided() {
                return (Class<? extends T>) provided.getClass();
            }

            @Override
            public R getReturned() {
                return ret;
            }

            @Override
            public void ret(R ret) {
                this.ret = ret;
            }

            @Override
            public Class<? extends R> returned() {
                return rCls;
            }
        };
    }

}
