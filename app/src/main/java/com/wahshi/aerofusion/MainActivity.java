package com.wahshi.aerofusion;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    final int BG = Color.rgb(5, 14, 24);
    final int SURFACE = Color.rgb(9, 27, 42);
    final int SURFACE2 = Color.rgb(12, 36, 55);
    final int CYAN = Color.rgb(50, 211, 246);
    final int GREEN = Color.rgb(76, 225, 162);
    final int AMBER = Color.rgb(255, 179, 71);
    final int RED = Color.rgb(255, 103, 111);
    final int TEXT = Color.rgb(236, 247, 252);
    final int MUTED = Color.rgb(145, 174, 192);
    final int BORDER = Color.rgb(29, 71, 96);

    EditText from, to;
    Spinner aircraft;
    ProgressBar progress;
    TextView buildState;
    LinearLayout overview, weather, route, brief;
    LinearLayout tabs;
    TextView[] tabButtons = new TextView[4];
    int selectedTab = 0;

    static class Airport {
        String icao, name;
        double lat, lon;
        String[] runwayNames;
        int[] runwayHeadings;
        Airport(String i, String n, double la, double lo, String[] rn, int[] rh) {
            icao=i; name=n; lat=la; lon=lo; runwayNames=rn; runwayHeadings=rh;
        }
    }

    static class Wx {
        String metar = "Unavailable";
        String taf = "Unavailable";
        String windText = "—";
        int windDir = -1;
        int windSpd = -1;
        int gust = -1;
        String visibility = "—";
        String qnh = "—";
        String temp = "—";
        String dew = "—";
        String cloud = "—";
        String phenomena = "None reported";
        boolean cavok = false;
        boolean ok = false;
    }

    final Map<String, Airport> airports = new HashMap<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        seedAirports();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("AERO", 22, CYAN, true);
        TextView fusion = text(" FUSION", 22, TEXT, true);
        titleRow.addView(logo);
        titleRow.addView(fusion);
        Space flex = new Space(this);
        titleRow.addView(flex, new LinearLayout.LayoutParams(0, 1, 1));
        TextView live = pill("LIVE DATA", GREEN);
        titleRow.addView(live);
        root.addView(titleRow);
        TextView subtitle = text("PILOT FLIGHT INTELLIGENCE", 10, MUTED, true);
        subtitle.setLetterSpacing(.16f);
        root.addView(subtitle);
        spacer(root, 16);

        LinearLayout setup = panel();
        TextView setupTitle = text("PLAN A FLIGHT", 12, MUTED, true);
        setup.addView(setupTitle);
        spacer(setup, 10);

        LinearLayout routeInput = new LinearLayout(this);
        routeInput.setOrientation(LinearLayout.HORIZONTAL);
        from = input("OOMS");
        to = input("OOSA");
        routeInput.addView(field("FROM", from), new LinearLayout.LayoutParams(0, dp(76), 1));
        TextView arrow = text("→", 22, CYAN, true);
        arrow.setGravity(Gravity.CENTER);
        routeInput.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(76)));
        routeInput.addView(field("TO", to), new LinearLayout.LayoutParams(0, dp(76), 1));
        setup.addView(routeInput);
        spacer(setup, 10);

        LinearLayout acRow = new LinearLayout(this);
        acRow.setOrientation(LinearLayout.HORIZONTAL);
        aircraft = new Spinner(this);
        aircraft.setAdapter(new AircraftAdapter(new String[]{"E175", "E190", "A320", "B737-8"}));
        acRow.addView(field("AIRCRAFT", aircraft), new LinearLayout.LayoutParams(0, dp(70), 1));
        spacer(acRow, 10);
        TextView profile = text("EFB PROFILE\nDEMO", 11, GREEN, true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(bg(SURFACE2, GREEN, 14, 1));
        acRow.addView(profile, new LinearLayout.LayoutParams(dp(112), dp(70)));
        setup.addView(acRow);
        spacer(setup, 12);

        Button build = new Button(this);
        build.setText("BUILD FLIGHT BRIEF");
        build.setTextSize(14);
        build.setTextColor(BG);
        build.setTypeface(null, 1);
        build.setAllCaps(false);
        build.setGravity(Gravity.CENTER);
        build.setPadding(dp(14), dp(11), dp(14), dp(11));
        build.setBackground(bg(CYAN, CYAN, 16, 0));
        setup.addView(build, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(setup);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(26), dp(26));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.setMargins(0, dp(12), 0, 0);
        root.addView(progress, pp);
        buildState = text("", 11, MUTED, false);
        buildState.setGravity(Gravity.CENTER);
        root.addView(buildState);

        spacer(root, 14);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"OVERVIEW", "WEATHER", "ROUTE", "BRIEF"};
        for (int i=0;i<labels.length;i++) {
            final int index=i;
            TextView tv=text(labels[i], 10, MUTED, true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(4), dp(10), dp(4), dp(10));
            tv.setOnClickListener(v -> selectTab(index));
            tabButtons[i]=tv;
            tabs.addView(tv, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        root.addView(tabs);
        spacer(root, 10);

        FrameLayout host = new FrameLayout(this);
        overview = section(); weather = section(); route = section(); brief = section();
        host.addView(overview); host.addView(weather); host.addView(route); host.addView(brief);
        root.addView(host, new LinearLayout.LayoutParams(-1, -2));

        spacer(root, 18);
        TextView foot = text("Planning aid only • Verify operational data with approved company/authority sources", 10, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        build.setOnClickListener(v -> buildFlight());
        selectTab(0);
        buildFlight();
    }

    void seedAirports() {
        airports.put("OOMS", new Airport("OOMS","Muscat International",23.5933,58.2844,new String[]{"08L/R","26L/R"},new int[]{83,263}));
        airports.put("OOSA", new Airport("OOSA","Salalah International",17.0387,54.0913,new String[]{"07","25"},new int[]{70,250}));
        airports.put("OMDB", new Airport("OMDB","Dubai International",25.2532,55.3657,new String[]{"12","30"},new int[]{120,300}));
        airports.put("OMDW", new Airport("OMDW","Al Maktoum International",24.8964,55.1614,new String[]{"12","30"},new int[]{120,300}));
        airports.put("OMAA", new Airport("OMAA","Zayed International",24.4330,54.6511,new String[]{"13","31"},new int[]{130,310}));
        airports.put("OMSJ", new Airport("OMSJ","Sharjah International",25.3286,55.5172,new String[]{"12","30"},new int[]{120,300}));
        airports.put("OTHH", new Airport("OTHH","Hamad International",25.2731,51.6081,new String[]{"16","34"},new int[]{160,340}));
        airports.put("OBBI", new Airport("OBBI","Bahrain International",26.2708,50.6336,new String[]{"12","30"},new int[]{120,300}));
        airports.put("OERK", new Airport("OERK","King Khalid International",24.9576,46.6988,new String[]{"15","33"},new int[]{150,330}));
        airports.put("OEJN", new Airport("OEJN","King Abdulaziz International",21.6796,39.1565,new String[]{"16","34"},new int[]{160,340}));
    }

    void buildFlight() {
        String f=from.getText().toString().trim().toUpperCase(Locale.US);
        String d=to.getText().toString().trim().toUpperCase(Locale.US);
        from.setText(f); to.setText(d);
        Airport a=airports.get(f), z=airports.get(d);
        clearSections();
        if (a==null || z==null) {
            overview.addView(infoCard("AIRPORT DATABASE", "This prototype currently includes the Gulf core airport set. Global AIRAC airport resolution is the next data connector.", AMBER));
            return;
        }

        double nm=distance(a,z);
        double trk=bearing(a,z);
        int tas = aircraft.getSelectedItem().toString().startsWith("E17") ? 420 : aircraft.getSelectedItem().toString().startsWith("E19") ? 430 : 445;
        int minutes=Math.max(20,(int)Math.round(nm/tas*60+18));

        overview.addView(routeHero(f,d,a,z,nm,trk,minutes));
        spacer(overview, 10);
        LinearLayout statusRow=new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.addView(stat("WEATHER","LOADING",CYAN),new LinearLayout.LayoutParams(0,dp(74),1));
        spacer(statusRow,8);
        statusRow.addView(stat("NAV DATA","LIMITED",AMBER),new LinearLayout.LayoutParams(0,dp(74),1));
        spacer(statusRow,8);
        statusRow.addView(stat("NOTAM","VERIFY",AMBER),new LinearLayout.LayoutParams(0,dp(74),1));
        overview.addView(statusRow);
        spacer(overview, 10);
        overview.addView(infoCard("PILOT ATTENTION", "• AIRAC/SID/STAR not yet connected\n• NOTAM feed not yet connected\n• Weather will populate from AviationWeather.gov\n\nThe app will never silently substitute estimated data for an unavailable operational source.", AMBER));

        route.addView(routeMap(a,z));
        spacer(route,10);
        route.addView(infoCard("ROUTE PREVIEW", String.format(Locale.US,"Great-circle: %.0f NM  •  Initial track: %.0f°\nCruise planning: FL350\n\nAIRAC waypoints, SID and STAR are intentionally not displayed until a licensed/current navigation-data source is connected.",nm,trk), CYAN));
        spacer(route,10);
        route.addView(infoCard("DATA STATUS", "Airport coordinates: prototype database\nAIRAC: not connected\nSID/STAR: not connected\nEn-route fixes: not connected\nSIGMET corridor: planned connector", AMBER));

        brief.addView(infoCard("60-SECOND BRIEF", String.format(Locale.US,"%s to %s • %.0f NM • Initial track %.0f° • Planned cruise FL350\n\nWeather is loading. Review departure/arrival wind, visibility, QNH and TAF. Verify NOTAM, SIGMET, dispatch route, performance, fuel and company procedures before operational use.",f,d,nm,trk), GREEN));
        spacer(brief,10);
        brief.addView(infoCard("DATA INTEGRITY", "LIVE: METAR / TAF\nPENDING: SIGMET corridor\nNOT CONNECTED: NOTAM / AIRAC / SID / STAR\n\nNo unverified route waypoint will be presented as operational navigation data.", AMBER));

        weather.addView(infoCard("WEATHER", "Fetching latest METAR and TAF for " + f + " and " + d + "…", CYAN));
        progress.setVisibility(View.VISIBLE);
        buildState.setText("Updating live weather…");
        new Thread(() -> {
            Wx dep=fetchWx(f); Wx arr=fetchWx(d);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                buildState.setText(dep.ok || arr.ok ? "Live weather updated" : "Weather unavailable — verify approved source");
                renderWeather(a,z,dep,arr);
                renderBrief(f,d,nm,trk,dep,arr);
                renderOverviewWeather(dep,arr);
            });
        }).start();
    }

    void renderOverviewWeather(Wx dep, Wx arr) {
        if (overview.getChildCount()<2) return;
        View v=overview.getChildAt(1);
        if (!(v instanceof LinearLayout)) return;
        LinearLayout row=(LinearLayout)v;
        if (row.getChildCount()>0 && row.getChildAt(0) instanceof LinearLayout) {
            LinearLayout chip=(LinearLayout)row.getChildAt(0);
            if (chip.getChildCount()>1 && chip.getChildAt(1) instanceof TextView) {
                TextView value=(TextView)chip.getChildAt(1);
                value.setText(dep.ok && arr.ok ? "LIVE" : "CHECK");
                value.setTextColor(dep.ok && arr.ok ? GREEN : AMBER);
            }
        }
    }

    void renderWeather(Airport a, Airport z, Wx dep, Wx arr) {
        weather.removeAllViews();
        weather.addView(weatherCard(a,dep,"DEPARTURE"));
        spacer(weather,10);
        weather.addView(runwayWindCard(a,dep));
        spacer(weather,12);
        weather.addView(weatherCard(z,arr,"ARRIVAL"));
        spacer(weather,10);
        weather.addView(runwayWindCard(z,arr));
        spacer(weather,12);
        weather.addView(infoCard("RAW REPORTS", a.icao + " METAR\n" + dep.metar + "\n\n" + a.icao + " TAF\n" + dep.taf + "\n\n" + z.icao + " METAR\n" + arr.metar + "\n\n" + z.icao + " TAF\n" + arr.taf, MUTED));
    }

    void renderBrief(String f,String d,double nm,double trk,Wx dep,Wx arr) {
        brief.removeAllViews();
        String depLine=dep.ok ? dep.windText+", VIS "+dep.visibility+", QNH "+dep.qnh : "weather unavailable";
        String arrLine=arr.ok ? arr.windText+", VIS "+arr.visibility+", QNH "+arr.qnh : "weather unavailable";
        String attention="";
        if ((dep.gust>0 && dep.gust>=25)||(arr.gust>0 && arr.gust>=25)) attention += "• Gusts 25 kt or greater — review limits/performance\n";
        if (!"None reported".equals(dep.phenomena)||!"None reported".equals(arr.phenomena)) attention += "• Reported weather phenomena — review details\n";
        if (attention.length()==0) attention="• No automatic weather trigger identified from the decoded METAR fields\n";
        String body=String.format(Locale.US,"%s → %s\n%.0f NM • Track %.0f° • FL350 planning\n\nDEPARTURE  %s\nARRIVAL      %s\n\nATTENTION\n%s\nMANDATORY CHECKS\n• Official NOTAM / AIP / charts\n• SIGMET and en-route weather\n• Dispatch route / fuel / alternate\n• Aircraft performance and company procedures",f,d,nm,trk,depLine,arrLine,attention);
        brief.addView(infoCard("PILOT BRIEF",body,GREEN));
        spacer(brief,10);
        brief.addView(infoCard("INTEGRITY NOTE","Decoded weather is a convenience layer. The raw METAR/TAF remains available under WEATHER. Route/nav data remains screening-only until AIRAC and NOTAM sources are connected.",AMBER));
    }

    View weatherCard(Airport ap, Wx w, String phase) {
        LinearLayout c=cardBase(w.ok ? CYAN : AMBER);
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView phaseTv=text(phase,10,MUTED,true); head.addView(phaseTv);
        Space s=new Space(this); head.addView(s,new LinearLayout.LayoutParams(0,1,1));
        head.addView(pill(w.ok ? "LIVE" : "CHECK", w.ok ? GREEN : AMBER));
        c.addView(head);
        spacer(c,6);
        c.addView(text(ap.icao+"  "+ap.name,18,TEXT,true));
        spacer(c,12);
        LinearLayout r1=new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(metric("WIND",w.windText,w.ok?CYAN:MUTED),new LinearLayout.LayoutParams(0,dp(66),1)); spacer(r1,6);
        r1.addView(metric("VIS",w.visibility,w.cavok?GREEN:TEXT),new LinearLayout.LayoutParams(0,dp(66),1)); spacer(r1,6);
        r1.addView(metric("QNH",w.qnh,TEXT),new LinearLayout.LayoutParams(0,dp(66),1));
        c.addView(r1); spacer(c,6);
        LinearLayout r2=new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(metric("TEMP/DEW",w.temp+" / "+w.dew,TEXT),new LinearLayout.LayoutParams(0,dp(66),1)); spacer(r2,6);
        r2.addView(metric("CLOUD",w.cloud,TEXT),new LinearLayout.LayoutParams(0,dp(66),1));
        c.addView(r2); spacer(c,10);
        c.addView(text("WX: "+w.phenomena,12,"None reported".equals(w.phenomena)?GREEN:AMBER,true));
        return c;
    }

    View runwayWindCard(Airport ap, Wx w) {
        LinearLayout c=cardBase(BORDER);
        c.addView(text("RUNWAY WIND COMPONENTS",11,MUTED,true));
        spacer(c,8);
        if (!w.ok || w.windDir<0 || w.windSpd<0) {
            c.addView(text("Wind direction/speed unavailable.",13,MUTED,false));
            return c;
        }
        for (int i=0;i<ap.runwayHeadings.length;i++) {
            int heading=ap.runwayHeadings[i];
            double angle=Math.toRadians(smallestAngle(w.windDir,heading));
            double head=w.windSpd*Math.cos(angle);
            double cross=Math.abs(w.windSpd*Math.sin(angle));
            String ht=head>=0 ? String.format(Locale.US,"HW %.0f kt",head) : String.format(Locale.US,"TW %.0f kt",Math.abs(head));
            String line=String.format(Locale.US,"RWY %-5s   %s   XW %.0f kt",ap.runwayNames[i],ht,cross);
            int col=head<0?AMBER:TEXT;
            c.addView(text(line,13,col,true));
        }
        spacer(c,6);
        c.addView(text("Screening calculation from METAR steady wind; verify actual runway, wind, gusts and aircraft/operator limits.",10,MUTED,false));
        return c;
    }

    Wx fetchWx(String id) {
        Wx w=new Wx();
        w.metar=fetchRaw("metar",id,"rawOb");
        w.taf=fetchRaw("taf",id,"rawTAF");
        w.ok=!w.metar.startsWith("Unavailable")&&!w.metar.startsWith("No report");
        if (w.ok) decodeMetar(w);
        return w;
    }

    String fetchRaw(String type,String id,String key) {
        HttpURLConnection c=null;
        try {
            URL u=new URL("https://aviationweather.gov/api/data/"+type+"?ids="+URLEncoder.encode(id,"UTF-8")+"&format=json");
            c=(HttpURLConnection)u.openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000);
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("User-Agent","AeroFusion/0.3 Android");
            int code=c.getResponseCode();
            if (code==204) return "No report returned.";
            if (code!=200) return "Unavailable (HTTP "+code+")";
            BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder s=new StringBuilder(); String line;
            while((line=r.readLine())!=null)s.append(line);
            JSONArray ar=new JSONArray(s.toString());
            if(ar.length()==0)return "No report returned.";
            JSONObject o=ar.getJSONObject(0);
            String raw=o.optString(key,"");
            if(raw.length()==0) raw=o.optString("raw_text","");
            return raw.length()>0?raw:"Report received; raw text unavailable.";
        } catch(Exception e) {
            return "Unavailable — check internet connection and verify approved source.";
        } finally { if(c!=null)c.disconnect(); }
    }

    void decodeMetar(Wx w) {
        String raw=w.metar.toUpperCase(Locale.US);
        Matcher m=Pattern.compile("\\b(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?KT\\b").matcher(raw);
        if(m.find()) {
            w.windDir="VRB".equals(m.group(1))?-1:Integer.parseInt(m.group(1));
            w.windSpd=Integer.parseInt(m.group(2));
            w.gust=m.group(3)==null?-1:Integer.parseInt(m.group(3));
            w.windText=m.group(1)+"/"+w.windSpd+(w.gust>0?"G"+w.gust:"")+" kt";
        }
        if(raw.contains(" CAVOK")) { w.cavok=true; w.visibility="CAVOK"; }
        else {
            Matcher v=Pattern.compile("\\s(\\d{4})\\s").matcher(raw);
            if(v.find()) w.visibility=v.group(1)+" m";
        }
        Matcher q=Pattern.compile("\\bQ(\\d{4})\\b").matcher(raw); if(q.find())w.qnh=q.group(1)+" hPa";
        Matcher td=Pattern.compile("\\b(M?\\d{2})/(M?\\d{2})\\b").matcher(raw);
        if(td.find()){w.temp=metTemp(td.group(1))+"°C";w.dew=metTemp(td.group(2))+"°C";}
        Matcher cl=Pattern.compile("\\b(FEW|SCT|BKN|OVC)(\\d{3})\\b").matcher(raw);
        StringBuilder clouds=new StringBuilder(); int count=0;
        while(cl.find()&&count<2){if(clouds.length()>0)clouds.append(" ");clouds.append(cl.group(1)).append(" ").append(Integer.parseInt(cl.group(2))*100).append("ft");count++;}
        if(clouds.length()>0)w.cloud=clouds.toString(); else if(w.cavok)w.cloud="CAVOK"; else if(raw.contains("NSC"))w.cloud="NSC";
        String[] wx={"TSRA","SHRA","FZRA","TS","RA","DZ","FG","BR","HZ","DU","SA"};
        StringBuilder ph=new StringBuilder();
        for(String x:wx) if(Pattern.compile("\\b"+x+"\\b").matcher(raw).find()){if(ph.length()>0)ph.append(", ");ph.append(x);}
        if(ph.length()>0)w.phenomena=ph.toString();
    }

    String metTemp(String s){return s.startsWith("M")?"-"+Integer.parseInt(s.substring(1)):String.valueOf(Integer.parseInt(s));}

    View routeHero(String f,String d,Airport a,Airport z,double nm,double trk,int minutes) {
        LinearLayout c=cardBase(CYAN);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(f,21,CYAN,true));
        TextView ar=text("   ✈   ",18,TEXT,true); ar.setGravity(Gravity.CENTER); top.addView(ar);
        top.addView(text(d,21,CYAN,true));
        Space flex=new Space(this); top.addView(flex,new LinearLayout.LayoutParams(0,1,1));
        top.addView(pill("E175",GREEN));
        c.addView(top);
        spacer(c,5);
        c.addView(text(a.name+" → "+z.name,12,MUTED,false));
        spacer(c,12);
        LinearLayout stats=new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(metric("DISTANCE",Math.round(nm)+" NM",TEXT),new LinearLayout.LayoutParams(0,dp(66),1)); spacer(stats,6);
        stats.addView(metric("EST TIME",minutes+" MIN",TEXT),new LinearLayout.LayoutParams(0,dp(66),1)); spacer(stats,6);
        stats.addView(metric("TRACK",Math.round(trk)+"°",TEXT),new LinearLayout.LayoutParams(0,dp(66),1));
        c.addView(stats);
        return c;
    }

    View routeMap(Airport a,Airport z) {
        RouteView rv=new RouteView(a,z);
        rv.setBackground(bg(SURFACE, BORDER, 18, 1));
        rv.setMinimumHeight(dp(210));
        return rv;
    }

    class RouteView extends View {
        Airport a,z; Paint p=new Paint(1);
        RouteView(Airport x,Airport y){super(MainActivity.this);a=x;z=y;setPadding(dp(16),dp(16),dp(16),dp(16));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.rgb(18,50,70));
            for(int i=1;i<5;i++){float x=w*i/5f;c.drawLine(x,0,x,h,p);}for(int i=1;i<4;i++){float y=h*i/4f;c.drawLine(0,y,w,y,p);}
            float x1=dp(30),y1=h*.68f,x2=w-dp(30),y2=h*.32f;
            Path path=new Path();path.moveTo(x1,y1);path.cubicTo(w*.35f,h*.22f,w*.65f,h*.78f,x2,y2);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setColor(CYAN);c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);p.setColor(GREEN);c.drawCircle(x1,y1,dp(7),p);p.setColor(AMBER);c.drawCircle(x2,y2,dp(7),p);
            p.setTextSize(dp(13));p.setFakeBoldText(true);p.setColor(TEXT);c.drawText(a.icao,x1,y1-dp(14),p);float tw=p.measureText(z.icao);c.drawText(z.icao,x2-tw,y2-dp(14),p);
            p.setTextSize(dp(10));p.setFakeBoldText(false);p.setColor(MUTED);c.drawText("GREAT-CIRCLE PREVIEW",dp(16),h-dp(16),p);
        }
    }

    void clearSections(){overview.removeAllViews();weather.removeAllViews();route.removeAllViews();brief.removeAllViews();}

    LinearLayout section(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setVisibility(View.GONE);return l;}

    void selectTab(int index){selectedTab=index;LinearLayout[] secs={overview,weather,route,brief};for(int i=0;i<secs.length;i++){secs[i].setVisibility(i==index?View.VISIBLE:View.GONE);tabButtons[i].setTextColor(i==index?CYAN:MUTED);tabButtons[i].setBackground(i==index?bg(SURFACE2,CYAN,12,1):bg(Color.TRANSPARENT,Color.TRANSPARENT,12,0));}}

    LinearLayout panel(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(bg(SURFACE,BORDER,18,1));return l;}
    LinearLayout cardBase(int accent){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(bg(SURFACE,accent,18,1));return l;}

    View infoCard(String title,String body,int accent){LinearLayout c=cardBase(accent);c.addView(text(title,12,accent,true));spacer(c,7);c.addView(text(body,13,TEXT,false));return c;}

    View stat(String label,String value,int color){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER);l.setBackground(bg(SURFACE2,BORDER,14,1));l.addView(text(label,9,MUTED,true));TextView v=text(value,12,color,true);v.setGravity(Gravity.CENTER);l.addView(v);return l;}

    View metric(String label,String value,int color){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setPadding(dp(10),dp(7),dp(10),dp(7));l.setBackground(bg(SURFACE2,Color.TRANSPARENT,12,0));l.addView(text(label,9,MUTED,true));l.addView(text(value,13,color,true));return l;}

    LinearLayout field(String label, View child){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(10),dp(7),dp(10),dp(5));l.setBackground(bg(SURFACE2,BORDER,14,1));l.addView(text(label,9,MUTED,true));l.addView(child,new LinearLayout.LayoutParams(-1,0,1));return l;}

    EditText input(String initial){EditText e=new EditText(this);e.setText(initial);e.setSingleLine(true);e.setTextSize(19);e.setTextColor(TEXT);e.setSelectAllOnFocus(true);e.setGravity(Gravity.CENTER_VERTICAL);e.setPadding(0,0,0,0);e.setBackgroundColor(Color.TRANSPARENT);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);return e;}

    TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(null,1);v.setLineSpacing(0,1.12f);return v;}

    TextView pill(String s,int color){TextView v=text(s,9,color,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(9),dp(5),dp(9),dp(5));v.setBackground(bg(Color.argb(32,Color.red(color),Color.green(color),Color.blue(color)),color,999,1));return v;}

    GradientDrawable bg(int fill,int stroke,int radius,int strokeWidth){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(strokeWidth>0)g.setStroke(dp(strokeWidth),stroke);return g;}

    void spacer(LinearLayout parent,int d){Space s=new Space(this);parent.addView(s,new LinearLayout.LayoutParams(d==0?0:dp(d),d==0?0:dp(d)));}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    double distance(Airport a,Airport b){double R=3440.065,p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat),dp=Math.toRadians(b.lat-a.lat),dl=Math.toRadians(b.lon-a.lon);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return 2*R*Math.asin(Math.sqrt(h));}
    double bearing(Airport a,Airport b){double p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat),dl=Math.toRadians(b.lon-a.lon);double y=Math.sin(dl)*Math.cos(p2),x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);return(Math.toDegrees(Math.atan2(y,x))+360)%360;}
    int smallestAngle(int a,int b){int d=Math.abs(a-b)%360;return d>180?360-d:d;}

    class AircraftAdapter extends ArrayAdapter<String>{String[] items;AircraftAdapter(String[] x){super(MainActivity.this,android.R.layout.simple_spinner_item,x);items=x;}TextView make(int p){TextView t=text(items[p],16,TEXT,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(0,0,0,0);return t;}@Override public View getView(int p,View c,ViewGroup parent){return make(p);}@Override public View getDropDownView(int p,View c,ViewGroup parent){TextView t=make(p);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackgroundColor(SURFACE2);return t;}}
}
