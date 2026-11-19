package com.carrot.naver;

import android.util.Log;
import java.lang.reflect.Method;

/** Adds space above Naver's native safety group via its Compose slot padding. */
public final class SignalLayoutGap {
  private static Object state;
  private static Method read, write, padding, copyPadding, copySlot;
  private static Method[] slotFields, paddingFields;
  private static float gap;
  private static boolean unavailable;

  private static void bind(Class<?> slot) throws ReflectiveOperationException {
    ClassLoader loader = slot.getClassLoader();
    Class<?> compose = loader.loadClass("androidx.compose.runtime.SnapshotStateKt");
    Class<?> policy = loader.loadClass("androidx.compose.runtime.SnapshotMutationPolicy");
    Class<?> mutable = loader.loadClass("androidx.compose.runtime.MutableState");
    padding = slot.getMethod("n");
    Class<?> pad = padding.getReturnType();
    copyPadding = pad.getMethod("e", float.class, float.class, float.class, float.class);
    paddingFields = new Method[]{pad.getMethod("a"), pad.getMethod("b"), pad.getMethod("c"), pad.getMethod("d")};
    slotFields = new Method[]{slot.getMethod("a"), slot.getMethod("b"), slot.getMethod("d"),
        slot.getMethod("e"), slot.getMethod("f"), slot.getMethod("g")};
    copySlot = slot.getMethod("h", boolean.class, slotFields[1].getReturnType(), pad,
        slotFields[2].getReturnType(), slotFields[3].getReturnType(), slotFields[4].getReturnType(), boolean.class);
    read = mutable.getMethod("getValue");
    write = mutable.getMethod("setValue", Object.class);
    state = compose.getMethod("l", Object.class, policy, int.class, Object.class).invoke(null, gap, null, 2, null);
  }

  public static Object shiftSafety(Object slot) {
    if (unavailable || slot == null) return slot;
    try {
      if (state == null) bind(slot.getClass());
      float offset = ((Number) read.invoke(state)).floatValue();
      if (offset <= 0) return slot;
      Object pad = padding.invoke(slot);
      float top = ((Number) paddingFields[0].invoke(pad)).floatValue();
      if (!Float.isFinite(top)) return slot;
      Object shifted = copyPadding.invoke(pad, top + offset, paddingFields[1].invoke(pad),
          paddingFields[2].invoke(pad), paddingFields[3].invoke(pad));
      return copySlot.invoke(slot, slotFields[0].invoke(slot), slotFields[1].invoke(slot), shifted,
          slotFields[2].invoke(slot), slotFields[3].invoke(slot), slotFields[4].invoke(slot), slotFields[5].invoke(slot));
    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
      unavailable = true;
      Log.w("KisaSignalLayout", "Native safety slot unavailable", error);
      return slot;
    }
  }

  static void setHeight(float dp) {
    if (dp == gap || unavailable) return;
    gap = dp;
    if (state == null) return;
    try {
      write.invoke(state, dp);
    } catch (ReflectiveOperationException | RuntimeException error) {
      unavailable = true;
      Log.w("KisaSignalLayout", "Could not update safety slot gap", error);
    }
  }

  private SignalLayoutGap() {}
}
