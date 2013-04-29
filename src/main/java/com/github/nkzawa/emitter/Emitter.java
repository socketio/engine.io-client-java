package com.github.nkzawa.emitter;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class Emitter {

    private ConcurrentMap<String, ConcurrentLinkedQueue<Listener>> callbacks
            = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Listener>>();

    private ConcurrentMap<Listener, Listener> onceCallbacks = new ConcurrentHashMap<Listener, Listener>();


    public Emitter on(String event, Listener fn) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks == null) {
            callbacks = new ConcurrentLinkedQueue <Listener>();
            ConcurrentLinkedQueue<Listener> _callbacks = this.callbacks.putIfAbsent(event, callbacks);
            if (_callbacks != null) {
                callbacks = _callbacks;
            }
        }
        callbacks.add(fn);
        return this;
    }

    public Emitter once(final String event, final Listener fn) {
        Listener on = new Listener() {
            @Override
            public void call(Object... args) {
                Emitter.this.off(event, this);
                fn.call(args);
            }
        };

        this.onceCallbacks.put(fn, on);
        this.on(event, on);
        return this;
    }

    public Emitter off() {
        this.callbacks.clear();
        this.onceCallbacks.clear();
        return this;
    }

    public Emitter off(String event) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.remove(event);
        if (callbacks != null) {
            for (Listener fn : callbacks) {
                this.onceCallbacks.remove(fn);
            }
        }
        return this;
    }

    public Emitter off(String event, Listener fn) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks != null) {
            Listener off = this.onceCallbacks.remove(fn);
            callbacks.remove(off != null ? off : fn);
        }
        return this;
    }

    public Emitter emit(String event, Object... args) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks != null) {
            callbacks = new ConcurrentLinkedQueue<Listener>(callbacks);
            for (Listener fn : callbacks) {
                fn.call(args);
            }
        }
        return this;
    }

    public List<Listener> listeners(String event) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        return callbacks != null ?
                new ArrayList<Listener>(callbacks) : new ArrayList<Listener>();
    }

    public boolean hasListeners(String event) {
        return !this.listeners(event).isEmpty();
    }

    public static interface Listener {

        public void call(Object... args);
    }
}
