package ch.njol.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @deprecated use {@link Function} instead.
 */
@Deprecated(since = "2.10.0", forRemoval = true)
@FunctionalInterface
public interface Callback<R, A> extends Function<A, R> {

	@Nullable
	public R run(A arg);

	@Override
	default R apply(A a) {
		return run(a);
	}

}
