package com.parse.verloop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class WelcomeActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_welcome);

    Button loginButton = (Button) findViewById(R.id.login_button);
    loginButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
      }
    });

    Button signupButton = (Button) findViewById(R.id.signup_button);
    signupButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        startActivity(new Intent(WelcomeActivity.this, SignUpActivity.class));
      }
    });
  }
}
