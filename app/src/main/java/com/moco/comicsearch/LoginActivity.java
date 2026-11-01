package com.moco.comicsearch;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends SwipeBackActivity {

    private Site site;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private MaterialButton btnLogin;
    private TextView status;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_login);

        site = Site.get(this);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        btnLogin = findViewById(R.id.btnLogin);
        status = findViewById(R.id.status);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnLogin.setOnClickListener(v -> login());
    }

    private void login() {
        String user = inputUser.getText() == null ? "" : inputUser.getText().toString().trim();
        String pass = inputPass.getText() == null ? "" : inputPass.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            status.setText(R.string.login_fail);
            return;
        }
        status.setText(R.string.logging_in);
        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.6f);

        pool.execute(() -> {
            String message = null;
            boolean ok = false;
            try {
                JSONObject json = site.login(user, pass);
                int code = json.optInt("status", -1);
                ok = code == 1;
                if (!ok) {
                    message = errorText(json);
                }
            } catch (Exception e) {
                message = getString(R.string.login_fail);
            }
            final boolean success = ok;
            final String msg = message;
            runOnUiThread(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setAlpha(1f);
                if (success) {
                    site.prefs().setLoggedUser(user);
                    Toast.makeText(this, R.string.login_ok, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    status.setText(msg == null ? getString(R.string.login_fail) : msg);
                }
            });
        });
    }

    /** 站点的 errors 有时是数组、有时是字符串。 */
    private String errorText(JSONObject json) {
        Object err = json.opt("errors");
        String text = "";
        if (err instanceof org.json.JSONArray) {
            text = ((org.json.JSONArray) err).optString(0, "");
        } else if (err != null && err != JSONObject.NULL) {
            text = String.valueOf(err);
        }
        if (text.isEmpty()) {
            text = getString(R.string.login_fail);
        }
        return text;
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }
}
