package com.carrot.transport;
import org.json.*;import java.net.URI;import java.util.Locale;
/** Size/hash/path rules shared by both APK updaters. */
public final class UpdateRules {
 public static final long MAX_BYTES=1024L*1024*1024;
 static void size(long n){if(n<1024||n>MAX_BYTES)throw new IllegalArgumentException("invalid update size");}
 static void hash(String h){if(!h.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("invalid hash");}
 public static JSONObject plan(JSONObject manifest,String source,long installed)throws Exception {
  if(!source.equals("tmap")&&!source.equals("naver-map"))throw new IllegalArgumentException("unknown app");
  String pkg=source.equals("naver-map")?"com.nhn.android.nmap":"com.skt.tmap.ku";
  if(manifest.optInt("schema_version")==1){
   JSONArray apps=manifest.getJSONArray("apps");JSONObject row=null;for(int i=0;i<apps.length();i++)if(source.equals(apps.getJSONObject(i).optString("id"))){if(row!=null)throw new IllegalArgumentException("duplicate app");row=apps.getJSONObject(i);}
   if(row==null)throw new IllegalArgumentException("missing app");long build=row.optLong("update_build",0);if(build<=installed)return null;
   if(!pkg.equals(row.getString("package_name")))throw new IllegalArgumentException("wrong package");
   JSONObject apk=row.getJSONObject("apk"),archive=row.getJSONObject("archive");size(apk.getLong("bytes"));hash(apk.getString("sha256"));size(archive.getLong("bytes"));hash(archive.getString("sha256"));
   if(!"zip".equals(archive.getString("format"))||!apk.getString("filename").matches("[a-zA-Z0-9._-]+\\.apk"))throw new IllegalArgumentException("invalid archive");
   JSONArray parts=archive.getJSONArray("parts");if(parts.length()<1||parts.length()>16)throw new IllegalArgumentException("invalid parts");long total=0;
   for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);if(!part.getString("path").matches("packages/[a-zA-Z0-9._-]+"))throw new IllegalArgumentException("invalid part path");hash(part.getString("sha256"));long n=part.getLong("bytes");if(n<=0||n>MAX_BYTES)throw new IllegalArgumentException("invalid part size");total+=n;}
   if(total!=archive.getLong("bytes"))throw new IllegalArgumentException("part length mismatch");
   return new JSONObject().put("build",build).put("version",row.optString("modified_version",Long.toString(build))).put("package",pkg).put("size",apk.getLong("bytes")).put("sha256",apk.getString("sha256")).put("filename",apk.getString("filename")).put("archive",new JSONObject(archive.toString())).put("notes",row.optString("release_notes",""));
  }
  if(manifest.optInt("schema")!=1)throw new IllegalArgumentException("unsupported update schema");JSONObject p=manifest.getJSONObject("apps").getJSONObject(source);if(p.getLong("build")<=installed)return null;size(p.getLong("size"));hash(p.getString("sha256"));URI u=new URI(p.getString("url"));
  if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||!u.getPath().startsWith("/multikyd/navi_app/releases/download/"))throw new IllegalArgumentException("untrusted update URL");if(!pkg.equals(p.getString("package")))throw new IllegalArgumentException("wrong package");return new JSONObject(p.toString());
 }
 public static long resumeOffset(int status,String range,long offset,long expected){if(offset<0||offset>expected)throw new IllegalArgumentException("invalid partial size");if(status==200)return 0;if(offset>0&&status==206&&range!=null&&range.startsWith("bytes "+offset+"-")&&range.endsWith("/"+expected))return offset;if(offset==expected&&status==416)return offset;throw new IllegalArgumentException("invalid resume response: HTTP "+status);}
 public static boolean sameHash(String a,String b){return a!=null&&b!=null&&a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));}
}
