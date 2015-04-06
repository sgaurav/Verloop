package com.parse.verloop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.parse.ParseUser;

public class DispatchActivity extends Activity {

  public DispatchActivity() {
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (ParseUser.getCurrentUser() != null) {
      startActivity(new Intent(this, MainActivity.class));
    } else {
      startActivity(new Intent(this, WelcomeActivity.class));
    }
  }
}
