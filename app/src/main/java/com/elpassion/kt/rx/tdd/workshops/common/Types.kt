package com.elpassion.kt.rx.tdd.workshops.common

import io.reactivex.Observable

typealias Events = Observable<Any>

typealias Reducer<T> = (events: Events) -> Observable<T>