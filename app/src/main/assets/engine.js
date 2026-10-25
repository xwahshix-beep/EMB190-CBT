(function(root){
'use strict';
const airports={OOMS:'MUSCAT',OMDB:'DUBAI',OOSA:'SALALAH',OMAL:'AL AIN'};
const blank=()=>({origin:'',destination:'',flight:'',waypoints:[],runway:'',sid:'',arrival:'',approach:'',zfw:'',fuel:'',cruise:'',reserve:'',active:false,direct:''});
class Trainer{
constructor(saved){this.plan=blank(); if(saved&&saved.version===1&&saved.plan){for(const k of Object.keys(this.plan)){if(k==='waypoints'){if(Array.isArray(saved.plan[k]))this.plan[k]=saved.plan[k].filter(v=>/^DEM\d{2}$/.test(v)).slice(0,12);}else if(typeof saved.plan[k]===typeof this.plan[k])this.plan[k]=saved.plan[k];}}this.page='ROUTE';this.sub=0;this.scratch='';this.error='';this.pending=null;this.notice='Ready for familiarization';this.mode='guided';}
serialize(){return {version:1,plan:this.plan};}
fail(s){this.error=s;return false;}
key(k){if(k==='CLR'){if(this.error)this.error='';else this.scratch=this.scratch.slice(0,-1);return;}this.error='';if(k==='DEL')this.scratch='DELETE';else if(k==='SP')this.scratch+=' ';else if(this.scratch==='DELETE')this.scratch=k;else if(this.scratch.length<22)this.scratch+=k;}
nav(p){this.page=p;this.sub=0;this.error='';}
cycle(n){const max=this.page==='FPL'?Math.max(1,Math.ceil((this.plan.waypoints.length+1)/4)):this.page==='ROUTE'?2:1;this.sub=(this.sub+n+max)%max;this.error='';}
put(k,kind){const v=this.scratch.trim();if(!v){this.scratch=String(this.plan[k]);return true;}if(v==='DELETE'){this.plan[k]='';this.scratch='';this.plan.active=false;return true;}
if(kind==='airport'&&!airports[v])return this.fail('NOT IN TRAINING DB');
if(kind==='airport'&&v===this.plan[k==='origin'?'destination':'origin'])return this.fail('AIRPORTS MUST DIFFER');
if(kind==='flight'&&!/^[A-Z0-9]{2,8}$/.test(v))return this.fail('USE 2-8 LETTERS / DIGITS');
const ranges={zfw:[20,40],fuel:[0.1,10],cruise:[100,410],reserve:[0.1,5]};
if(ranges[k]){let n=Number(v.replace(/^FL/,''));if(!/^\d+(\.\d+)?$/.test(v.replace(/^FL/,''))||n<ranges[k][0]||n>ranges[k][1]||(k==='cruise'&&!Number.isInteger(n)))return this.fail('ENTRY OUT OF DEMO RANGE');this.plan[k]=String(n);}else this.plan[k]=v;
if(k==='origin'){this.plan.runway='';this.plan.sid='';}if(k==='destination'){this.plan.arrival='';this.plan.approach='';}
this.plan.active=false;this.scratch='';this.error='';this.notice='Entry accepted';return true;}
addWaypoint(){let v=this.scratch.trim();if(!/^DEM\d{2}$/.test(v))return this.fail('USE DEM01 TO DEM99');if(this.plan.waypoints.includes(v))return this.fail('DUPLICATE WAYPOINT');if(this.plan.waypoints.length>=12)return this.fail('TRAINING LIMIT: 12 FIXES');this.plan.waypoints.push(v);this.plan.active=false;this.scratch='';this.error='';this.notice='Training waypoint added';return true;}
activate(){if(!this.plan.origin||!this.plan.destination||!this.plan.waypoints.length)return this.fail('ENTER ROUTE AND WAYPOINT');this.plan.active=true;this.notice='Training route activated';this.error='';}
select(side,i){this.error='';const p=this.plan;
if(this.pending){if(side==='R'&&i===6){p.direct=this.pending;p.active=true;this.pending=null;this.notice='Direct-to training selection applied';this.nav('FPL');}else if(side==='L'&&i===6)this.pending=null;return;}
if(this.page==='INDEX'){const menus={L1:'ROUTE',R1:'FPL',L2:'NAV',R2:'PERF',L3:'DEP',R3:'ARR',L4:'DIR',R4:'RADIO',L5:'PROG'};if(menus[side+i])this.nav(menus[side+i]);return;}
if(this.page==='ROUTE'){if(!this.sub){if(i===1)this.put(side==='L'?'origin':'destination','airport');if(i===2&&side==='L')this.put('flight','flight');if(i===3)this.nav(side==='L'?'DEP':'ARR');if(i===4&&side==='L')this.sub=1;if(i===5&&side==='L')this.nav('PERF');}else{if(i<=4&&side==='L'){let idx=i-1;if(idx<p.waypoints.length){if(this.scratch==='DELETE'){p.waypoints.splice(idx,1);p.active=false;this.scratch='';}else this.scratch=p.waypoints[idx];}else this.addWaypoint();}if(i===5&&side==='L')this.addWaypoint();if(i===5&&side==='R')this.nav('FPL');}if(i===6)side==='R'?this.activate():this.nav('INDEX');return;}
if(this.page==='PERF'){const fields={L1:'zfw',R1:'fuel',L2:'cruise',R2:'reserve'};if(fields[side+i])this.put(fields[side+i]);if(i===6)this.nav(side==='L'?'INDEX':'ROUTE');return;}
if(this.page==='DEP'||this.page==='ARR'){if(!(this.page==='DEP'?p.origin:p.destination))return this.fail('SET AIRPORT ON ROUTE');if(i===2){if(this.page==='DEP'){p.runway=side==='L'?'RWY-A':'RWY-B';p.sid='SIM-DEP';}else{p.arrival=side==='L'?'SIM-ARR-A':'SIM-ARR-B';p.approach='SIM-APP';}p.active=false;this.notice='Synthetic procedure selected';}if(i===6)this.nav('ROUTE');return;}
if(this.page==='FPL'){if(i<=4&&side==='L'){const idx=this.sub*4+i-1;if(idx<p.waypoints.length){if(this.scratch==='DELETE'){const removed=p.waypoints.splice(idx,1)[0];if(p.direct===removed)p.direct='';p.active=false;this.scratch='';}else this.scratch=p.waypoints[idx];}}if(i===5&&side==='L')this.nav('DIR');if(i===6)side==='L'?this.nav('ROUTE'):this.activate();return;}
if(this.page==='DIR'){if(i===1&&side==='L'){if(!p.active)return this.fail('ACTIVATE ROUTE FIRST');if(!p.waypoints.includes(this.scratch))return this.fail('FIX NOT IN ROUTE');this.pending=this.scratch;this.scratch='';}if(i===6)this.nav('FPL');return;}
if(i===6)this.nav('INDEX');else if(this.page==='NAV'&&i===1&&side==='L')this.nav('ROUTE');else if(this.page==='NAV'&&i===2&&side==='L')this.nav('FPL');else if(this.page==='NAV'&&i===3&&side==='L')this.nav('DIR');else if(this.page==='RADIO')this.fail('RADIO TUNING NOT SIMULATED');}
rows(){const p=this.plan, r=Array.from({length:6},()=>['','','','']);let title=this.page,number='1/1';const a=(i,ll,lv,rl='',rv='')=>r[i-1]=[ll,lv,rl,rv];const v=x=>x||'□□□□';
if(this.pending){title='DIRECT TO · REVIEW';a(1,'SELECTED FIX',this.pending);a(3,'TRAINER ACTION','Change active fix only');a(4,'NO FLIGHT GUIDANCE','No track calculation');a(6,'','<CANCEL','','CONFIRM>');return {title,number,rows:r};}
if(this.page==='ROUTE'){title=(p.active?'ACT ':'MOD ')+'ROUTE';number=(this.sub+1)+'/2';if(!this.sub){a(1,'ORIGIN',v(p.origin),'DEST',v(p.destination));a(2,'FLIGHT ID',v(p.flight),'DATABASE','DEMO ONLY');a(3,'','<DEPARTURE','','ARRIVAL>');a(4,'','<WAYPOINTS','FIX COUNT',String(p.waypoints.length));a(5,'','<PERF INIT','ROUTE',p.active?'ACTIVE':'NOT ACTIVE');}else{for(let i=1;i<=4;i++)a(i,i===1?'TRAINING FIXES':'',p.waypoints[i-1]||'<ADD FIX');a(5,'','<APPEND FIX','','FLIGHT PLAN>');}a(6,'','<INDEX','',p.active?'ACTIVE':'ACTIVATE>');}
if(this.page==='INDEX'){title='MCDU MENU';a(1,'','<ROUTE','','FLIGHT PLAN>');a(2,'','<NAV','','PERF>');a(3,'','<DEPARTURE','','ARRIVAL>');a(4,'','<DIRECT TO','','RADIO>');a(5,'','<PROGRESS');a(6,'TRAINING EDITION','BY:WAHSHI');}
if(this.page==='PERF'){title='PERF INIT · DEMO';a(1,'ZFW (TONNES)',v(p.zfw),'FUEL (TONNES)',v(p.fuel));a(2,'CRUISE FL',v(p.cruise),'RESERVE (T)',v(p.reserve));a(3,'ENTRY PRACTICE ONLY','No performance model');a(4,'NOT CALCULATED','V-speeds / fuel burn');a(5,'RANGES ARE UI RULES','Not aircraft limits');a(6,'','<INDEX','','ROUTE>');}
if(this.page==='DEP'||this.page==='ARR'){let dep=this.page==='DEP';title=dep?'DEPARTURE · DEMO':'ARRIVAL · DEMO';a(1,'AIRPORT',v(dep?p.origin:p.destination),'DATA','SYNTHETIC');a(2,'SELECT',dep?'<RWY-A':'<SIM-ARR-A','SELECT',dep?'RWY-B>':'SIM-ARR-B>');a(3,'SELECTED',v(dep?p.runway:p.arrival));a(4,dep?'DEPARTURE':'APPROACH',v(dep?p.sid:p.approach));a(5,'NOT REAL PROCEDURES','No navigation use');a(6,'','<ROUTE');}
if(this.page==='FPL'){title=(p.active?'ACT ':'MOD ')+'FLIGHT PLAN';number=(this.sub+1)+'/'+Math.max(1,Math.ceil((p.waypoints.length+1)/4));for(let i=1;i<=4;i++){let idx=this.sub*4+i-1;let w=p.waypoints[idx];a(i,i===1?'FIX SEQUENCE':'',w?(p.direct===w?'→ ':'')+w:idx===p.waypoints.length?(p.destination||'NO DESTINATION'):'',w?'TYPE':'',w?'DEMO':'');}a(5,'','<DIRECT TO','STATUS',p.active?'ACTIVE':'MODIFIED');a(6,'','<ROUTE','',p.active?'ACTIVE':'ACTIVATE>');}
if(this.page==='DIR'){title='DIRECT TO · DEMO';a(1,'ENTER ROUTE FIX','□□□□');a(2,'CURRENT DIRECT FIX',p.direct||'NONE');a(3,'SELECT FROM ROUTE','Type a DEMxx fix');a(4,'REQUIRES ACTIVE ROUTE','Then press L1');a(6,'','<FLIGHT PLAN');}
if(this.page==='NAV'){title='NAV INDEX';a(1,'','<ROUTE');a(2,'','<FLIGHT PLAN');a(3,'','<DIRECT TO');a(4,'NAV DATABASE','SYNTHETIC / NO AIRAC');a(5,'POSITION / SENSORS','NOT SIMULATED');a(6,'','<INDEX');}
if(this.page==='PROG'){title='PROGRESS · TRAINER';a(1,'FROM',v(p.origin),'TO',v(p.destination));a(2,'FIXES',String(p.waypoints.length),'CRUISE FL',v(p.cruise));a(3,'ROUTE STATE',p.active?'ACTIVE':'MODIFIED');a(4,'ETA / DISTANCE / EFOB','NOT SIMULATED');a(5,'FLIGHT DYNAMICS','NOT SIMULATED');a(6,'','<INDEX');}
if(this.page==='RADIO'){title='RADIO · SCOPE';a(1,'RADIO TUNING','NOT IMPLEMENTED');a(3,'THIS EDITION','Route familiarization');a(6,'','<INDEX');}
return {title,number,rows:r};}
steps(){const p=this.plan;return [!!p.origin,!!p.destination,!!p.flight,p.waypoints.length>0,!!p.zfw,!!p.fuel,!!p.cruise,p.active];}
}
root.Trainer=Trainer;if(typeof module!=='undefined')module.exports={Trainer,airports};
})(typeof window==='undefined'?globalThis:window);
