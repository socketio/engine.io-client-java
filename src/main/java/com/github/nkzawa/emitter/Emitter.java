package com.github.nkzawa.emitter;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;


/**
 * The event emitter which is ported from the JavaScript module. This class is thread-safe.
 *
 * @see <a href="https://github.com/component/emitter">https://github.com/component/emitter</a>
 */
public class Emitter {

    private ConcurrentMap<String, ConcurrentLinkedQueue<Listener>> callbacks
            = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Listener>>();

    private ConcurrentMap<Listener, Listener> onceCallbacks = new ConcurrentHashMap<Listener, Listener>();


    /**
     * Listens on the event.
     * @param event event name.
     * @param fn
     * @return a reference to this object.
     */
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

    /**
     * Adds a one time listener for the event.
     *
     * @param event an event name.
     * @param fn
     * @return a reference to this object.
     */
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

    /**
     * Removes all registered listeners.
     *
     * @return a reference to this object.
     */
    public Emitter off() {
        this.callbacks.clear();
        this.onceCallbacks.clear();
        return this;
    }

    /**
     * Removes all listeners of the specified event.
     *
     * @param event an event name.
     * @return a reference to this object.
     */
    public Emitter off(String event) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.remove(event);
        if (callbacks != null) {
            for (Listener fn : callbacks) {
                this.onceCallbacks.remove(fn);
            }
        }
        return this;
    }

    /**
     * Removes the listener.
     *
     * @param event an event name.
     * @param fn
     * @return a reference to this object.
     */
    public Emitter off(String event, Listener fn) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        if (callbacks != null) {
            Listener off = this.onceCallbacks.remove(fn);
            callbacks.remove(off != null ? off : fn);
        }
        return this;
    }

    /**
     * Executes each of listeners with the given args.
     *
     * @param event an event name.
     * @param args
     * @return a reference to this object.
     */
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

    /**
     * Returns a list of listeners for the specified event.
     *
     * @param event an event name.
     * @return a reference to this object.
     */
    public List<Listener> listeners(String event) {
        ConcurrentLinkedQueue<Listener> callbacks = this.callbacks.get(event);
        return callbacks != null ?
                new ArrayList<Listener>(callbacks) : new ArrayList<Listener>();
    }

    /**
     * Check if this emitter has listeners for the specified event.
     *
     * @param event an event name.
     * @return a reference to this object.
     */
    public boolean hasListeners(String event) {
        return !this.listeners(event).isEmpty();
    }

    public static interface Listener {

        public void call(Object... args);
    }
}
