package com.elpassion.kt.rx.tdd.workshops.signup

import com.elpassion.kt.rx.tdd.workshops.assertLastValueThat
import com.elpassion.kt.rx.tdd.workshops.common.Events
import com.elpassion.kt.rx.tdd.workshops.common.Reducer
import com.jakewharton.rxrelay2.PublishRelay
import com.nhaarman.mockito_kotlin.*
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.MaybeSubject
import io.reactivex.subjects.SingleSubject
import org.junit.Test

class SignUpReducerTest {

    private val apiSubject = SingleSubject.create<Boolean>()
    private val permissionSubject = MaybeSubject.create<Unit>()
    private val camera = mock<() -> Unit>()
    private val api = mock<(String) -> Single<Boolean>> { on { invoke(any()) } doReturn apiSubject }
    private val events = PublishRelay.create<Any>()
    private val state = SignUpReducer(api, camera, { permissionSubject }).invoke(events).test()

    @Test
    fun shouldLoginValidationStateBeIdleAtTheBegging() {
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.IDLE }
    }

    @Test
    fun shouldLoginValidationStateBeInProgressWhenNotEmptyLoginArrives() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.LOADING }
    }

    @Test
    fun shouldLoginValidationStateBeIdleAfterErasingLogin() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        events.accept(SignUp.LoginValidation.LoginChangedEvent(""))
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.IDLE }
    }

    @Test
    fun shouldLoginValidationStateBeAvailableWhenApiPasses() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        apiSubject.onSuccess(true)
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.LOGIN_AVAILABLE }
    }

    @Test
    fun shouldLoginValidationStateBeNotAvailableWhenApiReturnsThatItIsTaken() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        apiSubject.onSuccess(false)
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.LOGIN_TAKEN }
    }

    @Test
    fun shouldValidateLoginUsingPassedLogin() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        verify(api).invoke("login")
    }

    @Test
    fun shouldShowErrorWhenApiReturnsError() {
        events.accept(SignUp.LoginValidation.LoginChangedEvent("login"))
        apiSubject.onError(RuntimeException())
        state.assertLastValueThat { loginValidation == SignUp.LoginValidation.State.ERROR }
    }

    @Test
    fun shouldPhotoStateBeEmptyAtTheBegging() {
        state.assertLastValueThat { photo == SignUp.Photo.State.EMPTY }
    }

    @Test
    fun shouldCallCameraWhenAddingPhoto() {
        permissionSubject.onSuccess(Unit)
        events.accept(SignUp.Photo.TakePhotoEvent)
        verify(camera).invoke()
    }

    @Test
    fun shouldNotCallCameraWithoutPermissionsWhenAddingPhoto() {
        permissionSubject.onComplete()
        events.accept(SignUp.Photo.TakePhotoEvent)
        verify(camera, never()).invoke()
    }
}

class SignUpReducer(private val api: (login: String) -> Single<Boolean>,
                    private val camera: () -> Unit,
                    private val permissionSubject: () -> Maybe<Unit>) : Reducer<SignUp.State> {

    override fun invoke(events: Events): Observable<SignUp.State> {
        permissionSubject().subscribe { camera() }
        return validateLogin(events)
                .map { SignUp.State(it, SignUp.Photo.State.EMPTY) }
    }

    private fun validateLogin(events: Events): Observable<SignUp.LoginValidation.State> {
        return events
                .ofType(SignUp.LoginValidation.LoginChangedEvent::class.java)
                .switchMap {
                    if (it.login.isNotEmpty()) {
                        validateLoginWithApi(it.login)
                    } else {
                        Observable.just(SignUp.LoginValidation.State.IDLE)
                    }
                }
                .startWith(SignUp.LoginValidation.State.IDLE)
    }

    private fun validateLoginWithApi(login: String): Observable<SignUp.LoginValidation.State> {
        return api(login)
                .toObservable()
                .map {
                    if (it) {
                        SignUp.LoginValidation.State.LOGIN_AVAILABLE
                    } else {
                        SignUp.LoginValidation.State.LOGIN_TAKEN
                    }
                }
                .onErrorReturnItem(SignUp.LoginValidation.State.ERROR)
                .startWith(SignUp.LoginValidation.State.LOADING)
    }
}

interface SignUp {
    data class State(
            val loginValidation: LoginValidation.State,
            val photo: Photo.State)

    interface LoginValidation {
        enum class State {
            IDLE,
            LOADING,
            LOGIN_AVAILABLE,
            LOGIN_TAKEN,
            ERROR,
        }

        data class LoginChangedEvent(val login: String)
    }

    interface Photo {
        sealed class State {
            object EMPTY : State()
        }

        object TakePhotoEvent
    }
}

