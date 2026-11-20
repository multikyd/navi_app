import com.kisa.naverroute.PlaceMatch;
public final class TestPlaceSpacing {
 public static void main(String[] args){
  for(String q:new String[]{"수학의힘 신검단캠퍼스","수학의 힘 신 검단캠퍼스","수학의힘신검단캠퍼스","수학의 힘 신 검단캠퍼스 학원"}){
   if(!PlaceMatch.exact(q,"수학의힘 신검단캠퍼스학원"))throw new AssertionError(q);
   if(!PlaceMatch.exact(q,"수학의힘 신검단캠퍼스"))throw new AssertionError("Optional suffix "+q);
   if(PlaceMatch.exact(q,"수학의힘 검단캠퍼스학원"))throw new AssertionError("Wrong branch "+q);
  }
  if(!PlaceMatch.compactKoreanSpaces("수학의 힘 신 검단캠퍼스").equals("수학의힘신검단캠퍼스"))throw new AssertionError("Spacing");
  if(!PlaceMatch.compactKoreanSpaces("A B 123 456").equals("A B 123 456"))throw new AssertionError("Unrelated tokens changed");
  if(PlaceMatch.exact("수학의힘 신검단캠퍼스","수학의힘 신검단캠퍼스 2관"))throw new AssertionError("Different site");
  System.out.println("PASS: spacing, optional academy suffix, branch separation and unchanged non-Korean tokens");
 }
}
