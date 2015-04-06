package com.parse.verloop;

import com.parse.ParseClassName;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

@ParseClassName("Posts")
public class VerloopPost extends ParseObject {
  public String getTitle() {
        return getString("title");
    }

  public void setTitle(String value) {
        put("title", value);
    }

  public String getText() {
    return getString("text");
  }

  public void setText(String value) {
    put("text", value);
  }

  public ParseUser getUser() {
    return getParseUser("user");
  }

  public void setUser(ParseUser value) {
    put("user", value);
  }

  public ParseGeoPoint getLocation() {
    return getParseGeoPoint("location");
  }

  public void setLocation(ParseGeoPoint value) {
    put("location", value);
  }

  public static ParseQuery<VerloopPost> getQuery() {
    return ParseQuery.getQuery(VerloopPost.class);
  }
}
