package java.util.regex;

public class PatternSyntaxException extends IllegalArgumentException {
  public PatternSyntaxException(String description, String regex, int index) { super(description); }
  public String getDescription() { return null; }
  public int getIndex() { return 0; }
  public String getPattern() { return null; }
}
