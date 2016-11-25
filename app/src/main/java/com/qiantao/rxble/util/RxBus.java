package com.qiantao.rxble.util;


import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * Created by qiantao on 2016/11/18.
 * RxJava 实现event bus
 */

public class RxBus {
    private final Subject<Object,Object> mBus = new SerializedSubject<>(PublishSubject.create());

    private static class Singleton{
        private static final RxBus INSTANCE = new RxBus();
    }

    private RxBus() {}

    public static RxBus getInstance() {
        return Singleton.INSTANCE;
    }

    public void send(Object o) {
        mBus.onNext(o);
    }

    public boolean hasObservers() {
        return mBus.hasObservers();
    }

    public Observable<Object> toObservable() {
        return mBus;
    }
}
