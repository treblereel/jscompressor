package org.treblereel.javascript.compiler.domain;

import java.util.Objects;

public class LanguageInOut {

  private String languageIn;
  private String languageOut;

  public LanguageInOut() {}

  public LanguageInOut(String languageIn, String languageOut) {
    this.languageIn = languageIn;
    this.languageOut = languageOut;
  }

  public String getLanguageIn() {
    return languageIn;
  }

  public void setLanguageIn(String languageIn) {
    this.languageIn = languageIn;
  }

  public String getLanguageOut() {
    return languageOut;
  }

  public void setLanguageOut(String languageOut) {
    this.languageOut = languageOut;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LanguageInOut that = (LanguageInOut) o;
    return Objects.equals(languageIn, that.languageIn) && Objects.equals(languageOut, that.languageOut);
  }

  @Override
  public int hashCode() {
    return Objects.hash(languageIn, languageOut);
  }

  @Override
  public String toString() {
    return "LanguageInOut{" +
            "languageIn='" + languageIn + '\'' +
            ", languageOut='" + languageOut + '\'' +
            '}';
  }
}
