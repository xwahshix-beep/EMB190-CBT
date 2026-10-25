package com.wahshi.e175mcdu;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.view.View;
import android.widget.FrameLayout;
import java.io.InputStream;
public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle state){super.onCreate(state);
 FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xff0b1218);
 web=new WebView(this);web.setBackgroundColor(0xff0b1218);frame.addView(web);setContentView(frame);
 frame.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
 WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setTextZoom(100);
 web.setWebChromeClient(new WebChromeClient(){ @Override public boolean onConsoleMessage(ConsoleMessage m){android.util.Log.i("MCDU",m.message());return true;} });
 web.setWebViewClient(new WebViewClient(){
 @Override public void onPageFinished(WebView v,String url){v.evaluateJavascript("(function(){return document.getElementById('scratch') && document.getElementById('pageTitle').textContent === 'MOD ROUTE' && document.querySelectorAll('.lsk').length === 12 ? 'MCDU_READY' : 'MCDU_NOT_READY:'+document.body.innerText.slice(0,300);})()",result->android.util.Log.i("MCDU",result));}
 @Override public void onReceivedError(WebView v,WebResourceRequest q,WebResourceError e){android.util.Log.e("MCDU","LOAD_ERROR:"+e.getDescription());}
 @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
 Uri u=request.getUrl();if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())){try{String path=u.getPath();if(path==null||!path.startsWith("/assets/")||path.contains(".."))throw new Exception();String name=path.substring(8);InputStream stream=getAssets().open(name);String mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":"application/javascript";return new WebResourceResponse(mime,"UTF-8",stream);}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,null);}}return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,null);}
 @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri u=request.getUrl();if("appassets.androidplatform.net".equals(u.getHost()))return false;if("https".equals(u.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}}return true;}
 });
 web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
 }
 @Override public void onBackPressed(){web.evaluateJavascript("(function(){var d=document.getElementById('modal');if(d&&d.open){d.close();return true;}return false;})()",value->{if(!"true".equals(value))new android.app.AlertDialog.Builder(this).setTitle("Exit trainer?").setMessage("Your session is saved on this device.").setPositiveButton("Exit",(d,w)->finish()).setNegativeButton("Stay",null).show();});}
 @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}
