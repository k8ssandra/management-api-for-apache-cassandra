/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.rpc;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

// Based on Google memoized supplier
public class LazyRef<T> implements Supplier<T>
{
    final Supplier<T> delegate;
    transient volatile boolean initialized;
    transient volatile T value;

    public static <T> LazyRef<T> of(Supplier<T> delegate)
    {
        Preconditions.checkNotNull(delegate);
        return new LazyRef<>(delegate);
    }

    private LazyRef(Supplier<T> delegate)
    {
        this.delegate = delegate;
    }

    public void doIfInitialized(Consumer<T> consumer)
    {
        if (initialized)
        {
            consumer.accept(value);
        }
        else
        {
            synchronized (this)
            {
                if (initialized)
                {
                    consumer.accept(value);
                }
            }
        }
    }

    @Override
    public T get()
    {
        if (!initialized)
        {
            synchronized (this)
            {
                if (!initialized)
                {
                    T t = delegate.get();
                    value = t;
                    initialized = true;
                    return t;
                }
            }
        }
        return value;
    }

    @Override
    public String toString()
    {
        if (initialized)
        {
            return "Uninitialized LazyRef";
        }
        else
        {
            return value.toString();
        }
    }
}
