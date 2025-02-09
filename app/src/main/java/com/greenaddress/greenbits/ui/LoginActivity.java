package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.os.Bundle;

import com.greenaddress.greenapi.ConnectionManager;
import com.greenaddress.greenapi.data.PinData;

import java.util.Observable;
import java.util.Observer;

public abstract class LoginActivity extends GaActivity implements Observer {

    protected void onLoggedIn() {
        final Intent intent = new Intent(LoginActivity.this, TabbedMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(getIntent().getData());
        intent.setAction(getIntent().getAction());
        startActivity(intent);
        finishOnUiThread();
    }

    protected void onLoginSuccess() { }
    protected void onLoginFailure() { }

    @Override
    protected void onCreateWithService(Bundle savedInstanceState) {
        // We add the observer on both create and resume, to be sure to be notified of connection
        // manager state changes, it's not a problem adding twice since per Observer documentation:
        // "Adds an observer to the set of observers for this object, provided
        // that it is not the same as some observer already in the set."
        mService.getConnectionManager().addObserver(this);
    }

    private synchronized void checkState() {
        final ConnectionManager cm = mService.getConnectionManager();
        try {
            if (cm.isLoggedIn()) {
                mService.onPostLogin();
                runOnUiThread(this::onLoginSuccess);
            } else if (cm.isLastLoginFailed()) {
                runOnUiThread(this::onLoginFailure);
            } else if (cm.isPostLogin()) {
                runOnUiThread(this::onLoggedIn);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResumeWithService() {
        super.onResumeWithService();
        if (mService != null) {
            final ConnectionManager cm = mService.getConnectionManager();
            cm.deleteObserver(this);
            cm.clearPreviousLoginError();
            checkState();
            cm.addObserver(this);
        }
    }

    protected void loginWithPin(String pin) {
        if (mService == null) {
            shortToast(R.string.id_you_are_not_connected);
            return;
        }
        final PinData pinData = PinData.fromPreferenceValues(mService.cfgPin());
        mService.getExecutor().execute(() -> {
            mService.resetSession();
            mService.getConnectionManager().loginWithPin(pin, pinData);
        });
    }

    @Override
    protected void onPauseWithService() {
        super.onPauseWithService();
        if (mService == null)
            return;
        mService.getConnectionManager().deleteObserver(this);
    }

    @Override
    public void update(final Observable observable, final Object o) {
        if (observable instanceof ConnectionManager) {
            checkState();
        }
    }

    protected int getCode(final Exception e) {
        try {
            final String stringCode = e.getMessage().split(" ")[1];
            return Integer.parseInt(stringCode);
        } catch (final Exception ignored) {}
        return 1;
    }
}
