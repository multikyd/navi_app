package com.kisa.transport;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/** Reads semantics labels without formatting actions or invoking Kotlin function references. */
public final class SemanticsDescription {
  static String text(Object value) {
    if (value instanceof String) return (String) value;
    if (value == null) return "";
    String type = value.getClass().getName();
    if (!type.equals("androidx.compose.ui.text.AnnotatedString") &&
        !type.equals("androidx.compose.ui.semantics.SemanticsPropertyKey")) return "";
    for (Field field : value.getClass().getDeclaredFields()) {
      if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) continue;
      try {
        field.setAccessible(true);
        Object result = field.get(value);
        if (result instanceof String) return (String) result;
      } catch (Exception ignored) {}
    }
    return "";
  }

  static void appendText(StringBuilder out, Object value) {
    String label = text(value);
    if (!label.isEmpty()) out.append(label).append(' ');
    if (value instanceof java.util.List<?>) {
      int count = 0;
      for (Object item : (java.util.List<?>) value) {
        if (++count > 64) break;
        label = text(item);
        if (!label.isEmpty()) out.append(label).append(' ');
      }
    }
  }

  public static String describe(Object configuration) {
    if (!(configuration instanceof Iterable<?>)) return "";
    StringBuilder result = new StringBuilder();
    int count = 0;
    for (Object item : (Iterable<?>) configuration) {
      if (++count > 128) break;
      if (!(item instanceof Map.Entry<?, ?>)) continue;
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
      String key = text(entry.getKey());
      if (key.isEmpty()) continue;
      result.append(key).append(' ');
      if (key.equals("Text") || key.equals("EditableText") ||
          key.equals("ContentDescription") || key.equals("StateDescription") ||
          key.equals("TestTag")) appendText(result, entry.getValue());
    }
    return result.toString();
  }
  private SemanticsDescription() {}
}
