import com.carrot.transport.SemanticsDescription;
import java.util.*;
public final class TestSemanticsDescription {
  static final class Poison {
    @Override public String toString() { throw new AssertionError("Action formatted"); }
  }
  static final class Config extends ArrayList<Map.Entry<String,Object>> {
    @Override public String toString() { throw new AssertionError("Config formatted"); }
  }
  public static void main(String[] args) {
    Config c = new Config();
    c.add(new AbstractMap.SimpleEntry<>("OnClick",new Poison()));
    c.add(new AbstractMap.SimpleEntry<>("RequestFocus",new Poison()));
    c.add(new AbstractMap.SimpleEntry<>("EditableText","query"));
    c.add(new AbstractMap.SimpleEntry<>("ContentDescription",Arrays.asList("signal 17 seconds",new Poison())));
    c.add(new AbstractMap.SimpleEntry<>("Text",new Poison()));
    String s = SemanticsDescription.describe(c);
    if (!s.contains("OnClick") || !s.contains("EditableText query") || !s.contains("signal 17 seconds"))
      throw new AssertionError("Required semantics lost: " + s);
    if (!SemanticsDescription.describe(new Poison()).isEmpty()) throw new AssertionError("Unexpected input");
    System.out.println("PASS: semantics labels preserved without formatting callbacks or configuration");
  }
}
