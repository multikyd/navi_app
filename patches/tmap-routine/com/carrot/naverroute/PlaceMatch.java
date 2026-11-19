package com.carrot.naverroute;
import java.text.Normalizer;
import java.util.Locale;
/** Name matching keeps branch words intact: 신검단 is never reduced to 검단. */
public final class PlaceMatch {
 public static String key(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 public static String compactKoreanSpaces(String query){return Normalizer.normalize(query==null?"":query,Normalizer.Form.NFKC).trim().replaceAll("(?<=[가-힣])\\s+(?=[가-힣])","");}
 static String campusKey(String text){String value=key(text);return value.endsWith("캠퍼스학원")?value.substring(0,value.length()-2):value;}
 public static boolean exact(String query,String name){String q=campusKey(query),n=campusKey(name);return !q.isEmpty()&&q.equals(n);}
 public static int voiceIndex(String text){String s=key(text);if(s.matches("(그래|응|네|예|좋아|맞아|거기로|안내해줘)(요)?"))return 0;String[] names={"첫","두","세","네","다섯"};for(int i=0;i<5;i++)if(s.equals((i+1)+"번")||s.equals((i+1)+"번째")||s.equals(names[i]+"번째"))return i;return -1;}
 private PlaceMatch(){}
}
