import React, { useState, useMemo, useEffect, useRef } from "react";
import {
  LayoutDashboard, Users, Search, Upload, Sliders, Bell, FileText, Settings,
  LogOut, Shield, Building2, ChevronRight, ChevronDown, Check, X, Plus, Mail,
  TrendingUp, TrendingDown, Briefcase, MapPin, GraduationCap, Award, Clock,
  Filter, Download, ArrowUpRight, Sparkles, Database, GitBranch, CircleCheck,
  CircleAlert, Send, Inbox, ShieldCheck, Activity, DollarSign, Link2, MoreHorizontal,
  ArrowRight, Layers, ScrollText, UserCog, Eye, Zap
} from "lucide-react";
import {
  ResponsiveContainer, PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis,
  Tooltip, LineChart, Line, CartesianGrid, AreaChart, Area
} from "recharts";

/* ============================== DESIGN TOKENS ============================== */
const C = {
  ink: "#0E1726", ink2: "#15203A", ink3: "#1D2B48", inkPanel: "#172441",
  inkLine: "#26314F", inkText: "#E7ECF6", inkMute: "#8A97B4",
  bone: "#F5F4EF", surface: "#FFFFFF", line: "#E5E2D9", line2: "#EEEDE6",
  text: "#172230", muted: "#657182",
  sapphire: "#2D4BC4", sapphireDk: "#21399B", sapphireSoft: "#E7ECFB",
  gold: "#A9791F", goldSoft: "#F3E9CF", goldLine: "#E3CE94",
  emerald: "#1C8A5A", emeraldSoft: "#DBF1E6",
  amber: "#C9791C", amberSoft: "#F8E8D2",
  red: "#BB3B2E", redSoft: "#F7DEDA",
  violet: "#6D4AA6", violetSoft: "#ECE4F7",
};
const F = {
  disp: "'Fraunces', Georgia, serif",
  ui: "'Inter', system-ui, sans-serif",
  mono: "'IBM Plex Mono', ui-monospace, monospace",
};

function GlobalStyle() {
  return (
    <style>{`
      @import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700&family=Inter:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap');
      * { box-sizing: border-box; }
      ::-webkit-scrollbar { width: 10px; height: 10px; }
      ::-webkit-scrollbar-thumb { background: #c9ccd6; border-radius: 8px; border: 3px solid transparent; background-clip: content-box; }
      ::-webkit-scrollbar-thumb:hover { background: #a9adba; background-clip: content-box; }
      .ai-fade { animation: aiFade .45s cubic-bezier(.2,.7,.2,1) both; }
      @keyframes aiFade { from { opacity:0; transform: translateY(8px);} to {opacity:1; transform:none;} }
      .ai-row { animation: aiRow .4s ease both; }
      @keyframes aiRow { from { opacity:0; transform: translateX(-6px);} to {opacity:1; transform:none;} }
      .ai-pulse { animation: aiPulse 1.4s ease-in-out infinite; }
      @keyframes aiPulse { 0%,100%{opacity:1} 50%{opacity:.4} }
      .ai-grow { animation: aiGrow .5s cubic-bezier(.2,.7,.2,1) both; }
      @keyframes aiGrow { from{ transform: scaleX(0); } to { transform: scaleX(1);} }
      @media (prefers-reduced-motion: reduce){ *{animation:none!important; transition:none!important;} }
      .lift { transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; }
      .lift:hover { transform: translateY(-2px); }
      .navitem { transition: background .15s ease, color .15s ease; }
      .tdrow { transition: background .12s ease; }
    `}</style>
  );
}

/* ============================== SEED DATA ============================== */
const TENANTS = [
  { id: "t1", name: "Universiti Teknologi Malaysia", short: "UTM", admin: "Norhafisah Zakaria", email: "norhafisah@utm.my", status: "active", since: "2026-02-11", users: 4, alumni: 1247, lastImport: "2026-05-28" },
  { id: "t2", name: "Universiti Malaya", short: "UM", admin: "Lim Wei Sheng", email: "wlim@um.edu.my", status: "active", since: "2026-03-04", users: 3, alumni: 982, lastImport: "2026-05-20" },
  { id: "t3", name: "Universiti Sains Malaysia", short: "USM", admin: "Aishah Rahman", email: "aishah@usm.my", status: "active", since: "2026-04-18", users: 2, alumni: 610, lastImport: "2026-05-12" },
];
const PENDING = [
  { id: "r1", name: "Ravi Chandran", email: "ravi@utp.edu.my", institution: "Universiti Teknologi PETRONAS", jobTitle: "Alumni Relations Lead", status: "pending", at: "2026-06-08" },
  { id: "r2", name: "Faridah Yusof", email: "faridah@uitm.edu.my", institution: "Universiti Teknologi MARA", jobTitle: "Career Services Officer", status: "pending", at: "2026-06-09" },
];

const FIRST = ["Ahmad","Nurul","Wei","Siti","Arjun","Mei","Hafiz","Aisyah","Daniel","Priya","Faiz","Tan","Sofia","Iqbal","Lina","Rajesh","Yusra","Chong","Amir","Nadia"];
const LAST = ["Fauzi","Hassan","Lim","Aminah","Kumar","Tan","Rahman","Ismail","Wong","Devi","Razak","Cheng","Karim","Singh","Yap","Nair","Abdullah","Lee","Salleh","Goh"];
const EMP = ["Petronas","Maybank","Grab","Shopee","Intel Malaysia","CIMB","AirAsia","Top Glove","Dell","Sime Darby","Khazanah","Axiata","Petron","Genting","Sunway","Astro","TM","Tenaga Nasional","Bursa Malaysia","Western Digital"];
const TITLE = ["Software Engineer","Senior Engineer","Data Analyst","Product Manager","Engineering Lead","Consultant","Finance Manager","Marketing Executive","Solutions Architect","Operations Director","VP Engineering","Research Scientist","UX Designer","Account Manager","CFO","Head of Data"];
const SENIORITY = ["Entry","Mid","Senior","Lead","Director","Executive"];
const IND = ["Technology","Banking & Finance","Telecommunications","Energy","Manufacturing","Consulting","Retail","Media"];
const CITY = ["Kuala Lumpur","Petaling Jaya","Cyberjaya","Penang","Johor Bahru","Singapore","Shah Alam","Subang Jaya"];

function rng(seed){ let s=seed; return ()=> (s=(s*1664525+1013904223)%4294967296)/4294967296; }
function buildAlumni(tenantId, n, seed){
  const r = rng(seed); const out=[];
  for(let i=0;i<n;i++){
    const fn=FIRST[Math.floor(r()*FIRST.length)], ln=LAST[Math.floor(r()*LAST.length)];
    const sen=SENIORITY[Math.floor(r()*SENIORITY.length)];
    const yr=2012+Math.floor(r()*13);
    const conf=Math.round((0.55+r()*0.44)*100)/100;
    const events=Math.floor(r()*4);
    const cap=Math.floor((r()*r())*900)+25; // skewed
    const likelihood=Math.min(98, Math.round(20+r()*78));
    out.push({
      id:`${tenantId}-a${i+1}`, tenantId, name:`${fn} ${ln}`,
      employer:EMP[Math.floor(r()*EMP.length)], title:TITLE[Math.floor(r()*TITLE.length)],
      seniority:sen, industry:IND[Math.floor(r()*IND.length)], city:CITY[Math.floor(r()*CITY.length)],
      gradYear:yr, confidence:conf, linkedin: r()>0.35, events,
      capacity: cap, likelihood, lastChange: `2026-0${1+Math.floor(r()*5)}-${10+Math.floor(r()*18)}`,
    });
  }
  return out;
}
const ALUMNI = { t1: buildAlumni("t1",1247,7), t2: buildAlumni("t2",982,19), t3: buildAlumni("t3",610,31) };

function careerHistory(a){
  // build a plausible rising trajectory
  const steps = 2 + (a.events||0);
  const senIdx = Math.max(1, SENIORITY.indexOf(a.seniority));
  const path=[]; let yr = a.gradYear;
  for(let i=0;i<steps;i++){
    const lvl = Math.min(senIdx, Math.round((i/(steps-1))*senIdx));
    path.push({
      year: yr,
      employer: i===steps-1 ? a.employer : EMP[(a.gradYear+i*7)%EMP.length],
      title: i===steps-1 ? a.title : TITLE[(a.gradYear+i*5)%TITLE.length],
      seniority: SENIORITY[lvl],
      event: i===0 ? "Graduated" : (lvl>SENIORITY.indexOf(path[i-1].seniority) ? "Promotion" : "Employer change"),
    });
    yr += 2 + ((a.gradYear+i)%3);
    if(yr>2026) yr=2026;
  }
  return path;
}

const ALERTS = [
  { id:"al1", type:"job_change", who:"Hafiz Rahman", detail:"Promoted to VP Engineering at Grab", pri:"high", at:"2h ago" },
  { id:"al2", type:"donor_prospect", who:"Priya Nair", detail:"Giving likelihood rose to 86% — capacity RM 300K–750K", pri:"high", at:"5h ago" },
  { id:"al3", type:"job_change", who:"Wei Lim", detail:"Changed employer: Intel → Western Digital", pri:"med", at:"1d ago" },
  { id:"al4", type:"verification", who:"Siti Aminah", detail:"Two records share a name — manual review suggested", pri:"med", at:"1d ago" },
  { id:"al5", type:"donor_prospect", who:"Daniel Wong", detail:"New high-value prospect identified (capacity RM 150K–400K)", pri:"high", at:"2d ago" },
  { id:"al6", type:"data_quality", who:"Import batch #B-0291", detail:"3 rows failed normalisation (LLM timeout)", pri:"low", at:"3d ago" },
  { id:"al7", type:"system", who:"Platform", detail:"Monthly tracking refresh completed for 1,247 records", pri:"low", at:"4d ago" },
];

const AUDIT = [
  { id:1, actor:"Norhafisah Zakaria", action:"ALUMNI_ANONYMISED", detail:"Record t1-a318 anonymised (PDPA request)", at:"2026-06-09 14:21" },
  { id:2, actor:"system", action:"IMPORT_COMPLETED", detail:"Batch B-0291 — 315 rows (198 new, 87 updated)", at:"2026-05-28 09:03" },
  { id:3, actor:"Norhafisah Zakaria", action:"USER_CREATED", detail:"Read-only user siti@utm.my added", at:"2026-05-22 11:40" },
  { id:4, actor:"superadmin", action:"PERMISSION_UPDATED", detail:"Salary Data enabled for UTM", at:"2026-05-20 16:12" },
  { id:5, actor:"Lina Karim", action:"REPORT_EXPORTED", detail:"Graduate Employability 2024 cohort (PDF)", at:"2026-05-19 10:55" },
];

const PERM_CATS = [
  { cat:"Employment Data", fields:[
    {k:"current_employment", label:"Current employment", on:true},
    {k:"location_linkedin", label:"Location + LinkedIn", on:false},
    {k:"employer_type", label:"Employer type", on:false},
    {k:"historical_employment", label:"Historical employment", on:false},
    {k:"nonprofit_boards", label:"Nonprofit boards", on:false},
    {k:"corp_matching", label:"Corporate matching gifts", on:false},
  ]},
  { cat:"Net Worth Data", fields:[
    {k:"salary", label:"Salary data", on:false},
    {k:"donation_pred", label:"Donation prediction", on:false},
    {k:"property", label:"Property values", on:false},
    {k:"sec_stock", label:"SEC / stock ownership", on:false},
  ]},
  { cat:"Contact Data", fields:[
    {k:"biz_email", label:"Business emails", on:false},
    {k:"personal_email", label:"Personal verified emails", on:false},
  ]},
  { cat:"Professional Insights", fields:[
    {k:"seniority", label:"Seniority levels", on:true},
    {k:"news", label:"News mentions", on:false},
  ]},
  { cat:"Data Refresh", fields:[
    {k:"monthly", label:"Monthly tracking", on:true},
    {k:"midyear", label:"Mid-year refresh", on:false},
    {k:"multiyear", label:"Multi-year agreements", on:false},
  ]},
  { cat:"Verification & Matching", fields:[
    {k:"ultra_conf", label:"Ultra-high confidence", on:false},
    {k:"company_id", label:"Company ID matching", on:false},
  ]},
  { cat:"Access & Support", fields:[
    {k:"exports_users", label:"Exports / users access", on:true},
    {k:"support", label:"Support & training", on:true},
  ]},
];

/* ============================== PRIMITIVES ============================== */
const cx = (...a) => a.filter(Boolean).join(" ");

function Btn({ children, kind="primary", sm, icon:Icon, onClick, dark, style }) {
  const base = { fontFamily:F.ui, fontWeight:600, fontSize:sm?12.5:13.5, borderRadius:10,
    padding:sm?"7px 12px":"9px 16px", display:"inline-flex", alignItems:"center", gap:8,
    cursor:"pointer", border:"1px solid transparent", letterSpacing:"-0.01em", transition:"all .15s ease" };
  const kinds = {
    primary:{ background:C.sapphire, color:"#fff", boxShadow:"0 1px 2px rgba(20,40,120,.25)" },
    gold:{ background:C.gold, color:"#fff" },
    ghost:{ background:"transparent", color: dark?C.inkText:C.text, border:`1px solid ${dark?C.inkLine:C.line}` },
    soft:{ background: dark?C.inkPanel:C.sapphireSoft, color: dark?C.inkText:C.sapphireDk },
    danger:{ background:C.redSoft, color:C.red, border:`1px solid ${C.red}22` },
  };
  return (
    <button onClick={onClick} className="lift" style={{...base, ...kinds[kind], ...style}}
      onMouseDown={e=>e.currentTarget.style.transform="translateY(0)"}>
      {Icon && <Icon size={sm?14:16} strokeWidth={2.2}/>}{children}
    </button>
  );
}

function Tag({ children, color=C.sapphire, soft, bg }) {
  return <span style={{ fontFamily:F.ui, fontSize:11, fontWeight:600, letterSpacing:"0.02em",
    color, background: bg||soft, padding:"3px 9px", borderRadius:999, whiteSpace:"nowrap" }}>{children}</span>;
}

function Toggle({ on, onClick }) {
  return (
    <button onClick={onClick} aria-pressed={on} style={{ width:38, height:22, borderRadius:999,
      background: on?C.emerald:"#CDD2DB", position:"relative", cursor:"pointer", border:"none",
      transition:"background .18s ease", flex:"none" }}>
      <span style={{ position:"absolute", top:2, left: on?18:2, width:18, height:18, borderRadius:999,
        background:"#fff", transition:"left .18s cubic-bezier(.2,.7,.2,1)", boxShadow:"0 1px 2px rgba(0,0,0,.2)" }}/>
    </button>
  );
}

function Card({ children, style, pad=20, dark, className }) {
  return <div className={className} style={{ background: dark?C.inkPanel:C.surface,
    border:`1px solid ${dark?C.inkLine:C.line}`, borderRadius:16, padding:pad, ...style }}>{children}</div>;
}

function Field({ label, value, onChange, placeholder, type="text" }) {
  return (
    <label style={{ display:"block" }}>
      <div style={{ fontFamily:F.ui, fontSize:12, fontWeight:600, color:C.muted, marginBottom:6 }}>{label}</div>
      <input value={value} onChange={e=>onChange?.(e.target.value)} placeholder={placeholder} type={type}
        style={{ width:"100%", fontFamily:F.ui, fontSize:14, color:C.text, padding:"10px 12px",
          border:`1px solid ${C.line}`, borderRadius:10, outline:"none", background:"#fff" }}
        onFocus={e=>e.target.style.borderColor=C.sapphire} onBlur={e=>e.target.style.borderColor=C.line}/>
    </label>
  );
}

function Avatar({ name, gold }) {
  const init = name.split(" ").map(s=>s[0]).slice(0,2).join("");
  return <div style={{ width:34, height:34, borderRadius:10, flex:"none",
    background: gold?C.goldSoft:C.sapphireSoft, color: gold?C.gold:C.sapphireDk,
    display:"grid", placeItems:"center", fontFamily:F.ui, fontWeight:700, fontSize:13 }}>{init}</div>;
}

const RM = n => "RM " + n.toLocaleString();

/* ============================== SIGNATURE: CAREER TRAJECTORY ============================== */
function Trajectory({ alum }) {
  const path = useMemo(()=>careerHistory(alum), [alum.id]);
  const levels = SENIORITY;
  return (
    <div style={{ position:"relative", padding:"28px 6px 8px" }}>
      <div style={{ display:"flex", alignItems:"flex-end", gap:0, position:"relative", minHeight:150 }}>
        {path.map((p,i)=>{
          const lvl = levels.indexOf(p.seniority);
          const h = 30 + lvl*22;
          const last = i===path.length-1;
          return (
            <div key={i} style={{ flex:1, display:"flex", flexDirection:"column", alignItems:"center",
              justifyContent:"flex-end", position:"relative" }} className="ai-fade">
              {i>0 && (
                <div style={{ position:"absolute", left:"-50%", bottom: h+18, width:"100%", height:2,
                  background:`linear-gradient(90deg, ${C.line}, ${C.sapphire})`, transformOrigin:"left" }} className="ai-grow"/>
              )}
              <div style={{ textAlign:"center", marginBottom:8 }}>
                <div style={{ fontFamily:F.ui, fontSize:12, fontWeight:700, color:C.text }}>{p.employer}</div>
                <div style={{ fontFamily:F.ui, fontSize:11, color:C.muted }}>{p.title}</div>
              </div>
              <div style={{ width: last?16:13, height: last?16:13, borderRadius:999, marginBottom:6,
                background: last?C.gold:C.sapphire, border:`3px solid ${last?C.goldSoft:C.sapphireSoft}`,
                boxShadow:`0 0 0 1px ${last?C.gold:C.sapphire}` }}/>
              <div style={{ width:2, height:h, background:`linear-gradient(${C.sapphire}, ${C.sapphireSoft})`, borderRadius:2 }}/>
              <div style={{ fontFamily:F.mono, fontSize:11, color:C.muted, marginTop:8 }}>{p.year}</div>
              <Tag color={p.event==="Promotion"?C.emerald:p.event==="Graduated"?C.violet:C.amber}
                soft={p.event==="Promotion"?C.emeraldSoft:p.event==="Graduated"?C.violetSoft:C.amberSoft}>{p.event}</Tag>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ============================== UNIVERSITY: DASHBOARD ============================== */
function KPI({ label, value, sub, trend, accent=C.sapphire }) {
  return (
    <Card className="lift" style={{ flex:1, minWidth:170 }}>
      <div style={{ fontFamily:F.ui, fontSize:12.5, fontWeight:600, color:C.muted, letterSpacing:"0.01em" }}>{label}</div>
      <div style={{ fontFamily:F.disp, fontSize:40, fontWeight:600, color:C.text, lineHeight:1.05, marginTop:8, letterSpacing:"-0.02em" }}>{value}</div>
      <div style={{ display:"flex", alignItems:"center", gap:6, marginTop:6 }}>
        {trend!=null && (trend>=0
          ? <span style={{ display:"inline-flex", alignItems:"center", gap:3, color:C.emerald, fontFamily:F.ui, fontSize:12, fontWeight:600 }}><TrendingUp size={13}/>+{trend}%</span>
          : <span style={{ display:"inline-flex", alignItems:"center", gap:3, color:C.red, fontFamily:F.ui, fontSize:12, fontWeight:600 }}><TrendingDown size={13}/>{trend}%</span>)}
        <span style={{ fontFamily:F.ui, fontSize:12, color:C.muted }}>{sub}</span>
      </div>
      <div style={{ height:3, background:accent, borderRadius:2, marginTop:14, opacity:.85 }}/>
    </Card>
  );
}

function Dashboard({ tenant, alumni }) {
  const indData = useMemo(()=>{
    const m={}; alumni.forEach(a=>m[a.industry]=(m[a.industry]||0)+1);
    return Object.entries(m).map(([name,value])=>({name,value})).sort((a,b)=>b.value-a.value);
  },[alumni]);
  const senData = useMemo(()=>{
    const m={}; SENIORITY.forEach(s=>m[s]=0); alumni.forEach(a=>m[a.seniority]++);
    return SENIORITY.map(s=>({name:s, value:m[s]}));
  },[alumni]);
  const trend = [
    {y:"2021",rate:71},{y:"2022",rate:76},{y:"2023",rate:79},{y:"2024",rate:82},{y:"2025",rate:84},{y:"2026",rate:86},
  ];
  const PIE = [C.sapphire,C.gold,C.emerald,C.violet,C.amber,"#3E78C9","#9C6BB3","#C2A24A"];
  const emp = 86, changes = 12;
  return (
    <div className="ai-fade">
      <PageHead title="Dashboard" sub={`Career intelligence overview · ${tenant.name}`}/>
      <div style={{ display:"flex", gap:16, flexWrap:"wrap", marginBottom:16 }}>
        <KPI label="Total alumni tracked" value={tenant.alumni.toLocaleString()} sub="in this tenant" trend={6} accent={C.sapphire}/>
        <KPI label="Employment rate" value={emp+"%"} sub="vs last cohort" trend={2} accent={C.emerald}/>
        <KPI label="Career-change alerts" value={changes} sub="this month" accent={C.amber}/>
        <KPI label="High-value prospects" value={alumni.filter(a=>a.likelihood>75).length} sub="donor likelihood >75%" trend={9} accent={C.gold}/>
      </div>
      <div style={{ display:"grid", gridTemplateColumns:"1.4fr 1fr", gap:16, marginBottom:16 }}>
        <Card>
          <CardTitle icon={Activity}>Employment rate trend</CardTitle>
          <ResponsiveContainer width="100%" height={210}>
            <AreaChart data={trend} margin={{left:-18,right:8,top:8}}>
              <defs><linearGradient id="g1" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={C.sapphire} stopOpacity={0.25}/>
                <stop offset="100%" stopColor={C.sapphire} stopOpacity={0}/></linearGradient></defs>
              <CartesianGrid strokeDasharray="3 3" stroke={C.line2} vertical={false}/>
              <XAxis dataKey="y" tick={{fontFamily:F.mono, fontSize:11, fill:C.muted}} axisLine={false} tickLine={false}/>
              <YAxis domain={[60,95]} tick={{fontFamily:F.mono, fontSize:11, fill:C.muted}} axisLine={false} tickLine={false}/>
              <Tooltip contentStyle={{fontFamily:F.ui, fontSize:12, borderRadius:10, border:`1px solid ${C.line}`}}/>
              <Area type="monotone" dataKey="rate" stroke={C.sapphire} strokeWidth={2.5} fill="url(#g1)"/>
            </AreaChart>
          </ResponsiveContainer>
        </Card>
        <Card>
          <CardTitle icon={Layers}>Seniority distribution</CardTitle>
          <ResponsiveContainer width="100%" height={210}>
            <BarChart data={senData} margin={{left:-22,right:8,top:8}}>
              <CartesianGrid strokeDasharray="3 3" stroke={C.line2} vertical={false}/>
              <XAxis dataKey="name" tick={{fontFamily:F.ui, fontSize:10.5, fill:C.muted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontFamily:F.mono, fontSize:11, fill:C.muted}} axisLine={false} tickLine={false}/>
              <Tooltip cursor={{fill:C.line2}} contentStyle={{fontFamily:F.ui, fontSize:12, borderRadius:10, border:`1px solid ${C.line}`}}/>
              <Bar dataKey="value" radius={[6,6,0,0]} fill={C.gold}/>
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>
      <div style={{ display:"grid", gridTemplateColumns:"1fr 1.4fr", gap:16 }}>
        <Card>
          <CardTitle icon={Briefcase}>Industry spread</CardTitle>
          <div style={{ display:"flex", alignItems:"center", gap:8 }}>
            <ResponsiveContainer width="55%" height={200}>
              <PieChart><Pie data={indData} dataKey="value" innerRadius={42} outerRadius={78} paddingAngle={2}>
                {indData.map((e,i)=><Cell key={i} fill={PIE[i%PIE.length]}/>)}
              </Pie>
              <Tooltip contentStyle={{fontFamily:F.ui, fontSize:12, borderRadius:10, border:`1px solid ${C.line}`}}/></PieChart>
            </ResponsiveContainer>
            <div style={{ flex:1 }}>
              {indData.slice(0,6).map((e,i)=>(
                <div key={i} style={{ display:"flex", alignItems:"center", gap:8, marginBottom:7 }}>
                  <span style={{ width:9, height:9, borderRadius:3, background:PIE[i%PIE.length], flex:"none" }}/>
                  <span style={{ fontFamily:F.ui, fontSize:12, color:C.text, flex:1 }}>{e.name}</span>
                  <span style={{ fontFamily:F.mono, fontSize:12, color:C.muted }}>{e.value}</span>
                </div>
              ))}
            </div>
          </div>
        </Card>
        <Card>
          <CardTitle icon={Bell}>Recent career events</CardTitle>
          {ALERTS.filter(a=>a.type==="job_change"||a.type==="donor_prospect").slice(0,5).map(a=>(
            <div key={a.id} className="tdrow" style={{ display:"flex", alignItems:"center", gap:12, padding:"10px 6px", borderBottom:`1px solid ${C.line2}` }}>
              <Avatar name={a.who} gold={a.type==="donor_prospect"}/>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:F.ui, fontSize:13, fontWeight:600, color:C.text }}>{a.who}</div>
                <div style={{ fontFamily:F.ui, fontSize:12, color:C.muted }}>{a.detail}</div>
              </div>
              <Tag color={a.type==="donor_prospect"?C.gold:C.emerald} soft={a.type==="donor_prospect"?C.goldSoft:C.emeraldSoft}>
                {a.type==="donor_prospect"?"Donor":"Promotion"}</Tag>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}

/* ============================== UNIVERSITY: ALUMNI DATABASE ============================== */
function AlumniDB({ alumni, perms }) {
  const [q,setQ]=useState(""); const [ind,setInd]=useState("All"); const [sen,setSen]=useState("All");
  const [sel,setSel]=useState(null);
  const rows = useMemo(()=> alumni.filter(a=>
    (q==="" || a.name.toLowerCase().includes(q.toLowerCase()) || a.employer.toLowerCase().includes(q.toLowerCase())) &&
    (ind==="All"||a.industry===ind) && (sen==="All"||a.seniority===sen)
  ).slice(0,40), [alumni,q,ind,sen]);
  return (
    <div className="ai-fade">
      <PageHead title="Alumni database" sub={`${alumni.length.toLocaleString()} records · tenant-isolated`}/>
      <Card pad={0} style={{ overflow:"hidden" }}>
        <div style={{ display:"flex", gap:10, padding:14, borderBottom:`1px solid ${C.line2}`, flexWrap:"wrap", alignItems:"center" }}>
          <div style={{ position:"relative", flex:1, minWidth:220 }}>
            <Search size={16} style={{ position:"absolute", left:12, top:11, color:C.muted }}/>
            <input value={q} onChange={e=>setQ(e.target.value)} placeholder="Search by name or employer…"
              style={{ width:"100%", padding:"9px 12px 9px 36px", border:`1px solid ${C.line}`, borderRadius:10, fontFamily:F.ui, fontSize:13.5, outline:"none" }}/>
          </div>
          <Dropdown value={ind} onChange={setInd} options={["All",...IND]} icon={Briefcase}/>
          <Dropdown value={sen} onChange={setSen} options={["All",...SENIORITY]} icon={Award}/>
          <Btn kind="ghost" sm icon={Download}>Export</Btn>
        </div>
        <div style={{ maxHeight:480, overflow:"auto" }}>
          <table style={{ width:"100%", borderCollapse:"collapse" }}>
            <thead><tr style={{ position:"sticky", top:0, background:"#fff", zIndex:1 }}>
              {["Alumnus","Current role","Industry","Grad","Confidence",""].map(h=>(
                <th key={h} style={{ textAlign:"left", fontFamily:F.ui, fontSize:11, fontWeight:600, color:C.muted,
                  padding:"10px 14px", borderBottom:`1px solid ${C.line}`, letterSpacing:"0.03em", textTransform:"uppercase" }}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {rows.map(a=>(
                <tr key={a.id} className="tdrow" style={{ cursor:"pointer" }} onClick={()=>setSel(a)}
                  onMouseEnter={e=>e.currentTarget.style.background=C.bone} onMouseLeave={e=>e.currentTarget.style.background="transparent"}>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}` }}>
                    <div style={{ display:"flex", alignItems:"center", gap:10 }}>
                      <Avatar name={a.name} gold={a.likelihood>80}/>
                      <div><div style={{ fontFamily:F.ui, fontSize:13.5, fontWeight:600, color:C.text }}>{a.name}
                        {a.linkedin && <Link2 size={12} style={{ marginLeft:6, color:C.sapphire, verticalAlign:"middle" }}/>}</div>
                      <div style={{ fontFamily:F.mono, fontSize:11, color:C.muted }}>{a.id}</div></div>
                    </div>
                  </td>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}` }}>
                    <div style={{ fontFamily:F.ui, fontSize:13, color:C.text }}>{a.title}</div>
                    <div style={{ fontFamily:F.ui, fontSize:12, color:C.muted }}>{a.employer} · {a.seniority}</div></td>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}` }}><Tag color={C.sapphireDk} soft={C.sapphireSoft}>{a.industry}</Tag></td>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}`, fontFamily:F.mono, fontSize:12.5, color:C.text }}>{a.gradYear}</td>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}` }}>
                    <ConfBar v={a.confidence}/></td>
                  <td style={{ padding:"11px 14px", borderBottom:`1px solid ${C.line2}` }}><ChevronRight size={16} color={C.muted}/></td>
                </tr>
              ))}
              {rows.length===0 && <tr><td colSpan={6} style={{ padding:40, textAlign:"center", fontFamily:F.ui, color:C.muted }}>
                No alumni found. Try broadening your search.</td></tr>}
            </tbody>
          </table>
        </div>
      </Card>
      {sel && <ProfileDrawer alum={sel} onClose={()=>setSel(null)} perms={perms}/>}
    </div>
  );
}

function ConfBar({ v }) {
  const c = v>=0.85?C.emerald : v>=0.7?C.amber : C.red;
  return <div style={{ display:"flex", alignItems:"center", gap:8 }}>
    <div style={{ width:60, height:6, borderRadius:99, background:C.line2 }}>
      <div style={{ width:`${v*100}%`, height:"100%", borderRadius:99, background:c }}/>
    </div>
    <span style={{ fontFamily:F.mono, fontSize:11.5, color:c, fontWeight:600 }}>{Math.round(v*100)}%</span>
  </div>;
}

function ProfileDrawer({ alum, onClose, perms }) {
  return (
    <div style={{ position:"fixed", inset:0, zIndex:50 }}>
      <div onClick={onClose} style={{ position:"absolute", inset:0, background:"rgba(14,23,38,.42)", backdropFilter:"blur(2px)" }} className="ai-fade"/>
      <div style={{ position:"absolute", right:0, top:0, bottom:0, width:"min(720px, 94vw)", background:C.bone,
        boxShadow:"-20px 0 60px rgba(0,0,0,.2)", overflow:"auto" }} className="ai-row">
        <div style={{ padding:"22px 26px", background:"#fff", borderBottom:`1px solid ${C.line}`, display:"flex", alignItems:"center", gap:14, position:"sticky", top:0, zIndex:2 }}>
          <Avatar name={alum.name} gold={alum.likelihood>80}/>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:F.disp, fontSize:24, fontWeight:600, color:C.text, letterSpacing:"-0.01em" }}>{alum.name}</div>
            <div style={{ fontFamily:F.ui, fontSize:13, color:C.muted }}>{alum.title} · {alum.employer} · <MapPin size={11} style={{verticalAlign:"middle"}}/> {alum.city}</div>
          </div>
          <button onClick={onClose} style={{ border:"none", background:C.bone, borderRadius:10, padding:8, cursor:"pointer" }}><X size={18} color={C.muted}/></button>
        </div>
        <div style={{ padding:26 }}>
          <div style={{ display:"flex", gap:12, marginBottom:18, flexWrap:"wrap" }}>
            <MiniStat label="Seniority" value={alum.seniority}/>
            <MiniStat label="Industry" value={alum.industry}/>
            <MiniStat label="Graduated" value={alum.gradYear}/>
            <MiniStat label="Match confidence" value={Math.round(alum.confidence*100)+"%"} color={alum.confidence>=.85?C.emerald:C.amber}/>
          </div>
          <Card pad={18} style={{ marginBottom:16 }}>
            <CardTitle icon={GitBranch}>Career trajectory</CardTitle>
            <Trajectory alum={alum}/>
          </Card>
          <Card pad={18}>
            <CardTitle icon={DollarSign}>Donor insight</CardTitle>
            {perms.donation_pred || perms.salary ? (
              <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>
                <Gauge label="Giving likelihood" v={alum.likelihood}/>
                <div>
                  <MiniStat label="Estimated capacity" value={`${RM(alum.capacity*1000)} – ${RM(alum.capacity*1000*3)}`} color={C.gold}/>
                  <div style={{ height:10 }}/>
                  <MiniStat label="Wealth indicator" value={alum.capacity>300?"High":alum.capacity>120?"Medium":"Low"} color={C.gold}/>
                </div>
              </div>
            ) : (
              <div style={{ fontFamily:F.ui, fontSize:13, color:C.muted, padding:"8px 0" }}>
                <ShieldCheck size={15} style={{verticalAlign:"middle", marginRight:6, color:C.muted}}/>
                Net-worth fields are disabled for this tenant. Enable “Donation prediction” in Data Permissions to surface giving insights.
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
function MiniStat({ label, value, color=C.text }) {
  return <div style={{ background:"#fff", border:`1px solid ${C.line}`, borderRadius:12, padding:"10px 14px", minWidth:120 }}>
    <div style={{ fontFamily:F.ui, fontSize:11, fontWeight:600, color:C.muted }}>{label}</div>
    <div style={{ fontFamily:F.ui, fontSize:15, fontWeight:600, color, marginTop:2 }}>{value}</div></div>;
}
function Gauge({ label, v }) {
  return <div>
    <div style={{ fontFamily:F.ui, fontSize:11, fontWeight:600, color:C.muted, marginBottom:8 }}>{label}</div>
    <div style={{ position:"relative", height:64, display:"flex", alignItems:"center", gap:12 }}>
      <div style={{ fontFamily:F.disp, fontSize:38, fontWeight:600, color:C.gold }}>{v}%</div>
      <div style={{ flex:1 }}>
        <div style={{ height:10, borderRadius:99, background:C.line2 }}>
          <div className="ai-grow" style={{ width:`${v}%`, height:"100%", borderRadius:99, background:`linear-gradient(90deg, ${C.goldLine}, ${C.gold})`, transformOrigin:"left" }}/>
        </div>
      </div>
    </div>
  </div>;
}

/* ============================== UNIVERSITY: DONOR INSIGHTS ============================== */
function Donor({ alumni, perms }) {
  const [sort,setSort]=useState("likelihood");
  const locked = !(perms.donation_pred || perms.salary);
  const rows = useMemo(()=> [...alumni].sort((a,b)=> sort==="capacity"? b.capacity-a.capacity : sort==="recent"? (a.lastChange<b.lastChange?1:-1) : b.likelihood-a.likelihood).slice(0,18),[alumni,sort]);
  return (
    <div className="ai-fade">
      <PageHead title="Donor insights" sub="Prospects ranked by giving signal"/>
      {locked ? (
        <Card style={{ textAlign:"center", padding:48 }}>
          <ShieldCheck size={34} color={C.gold} style={{margin:"0 auto 12px"}}/>
          <div style={{ fontFamily:F.disp, fontSize:22, color:C.text, marginBottom:6 }}>Net-worth insights are locked</div>
          <div style={{ fontFamily:F.ui, fontSize:13.5, color:C.muted, maxWidth:440, margin:"0 auto" }}>
            The platform operator controls net-worth fields per institution. Ask your account manager to enable Donation prediction to unlock this module.</div>
        </Card>
      ) : (
        <Card pad={0} style={{ overflow:"hidden" }}>
          <div style={{ display:"flex", gap:8, padding:14, borderBottom:`1px solid ${C.line2}`, alignItems:"center" }}>
            <span style={{ fontFamily:F.ui, fontSize:12.5, color:C.muted, fontWeight:600 }}>Sort by</span>
            {[["likelihood","Likelihood"],["capacity","Capacity"],["recent","Recent activity"]].map(([k,l])=>(
              <button key={k} onClick={()=>setSort(k)} className="navitem" style={{ border:"none", cursor:"pointer", borderRadius:8,
                padding:"6px 12px", fontFamily:F.ui, fontSize:12.5, fontWeight:600,
                background: sort===k?C.goldSoft:"transparent", color: sort===k?C.gold:C.muted }}>{l}</button>
            ))}
          </div>
          <div style={{ maxHeight:480, overflow:"auto" }}>
            {rows.map((a,i)=>(
              <div key={a.id} className="tdrow ai-row" style={{ display:"flex", alignItems:"center", gap:14, padding:"13px 16px", borderBottom:`1px solid ${C.line2}` }}>
                <div style={{ fontFamily:F.mono, fontSize:13, color:C.muted, width:22 }}>{i+1}</div>
                <Avatar name={a.name} gold/>
                <div style={{ flex:1, minWidth:0 }}>
                  <div style={{ fontFamily:F.ui, fontSize:14, fontWeight:600, color:C.text }}>{a.name}</div>
                  <div style={{ fontFamily:F.ui, fontSize:12, color:C.muted }}>{a.title} · {a.employer}</div>
                </div>
                <div style={{ width:140 }}><Gauge label="Likelihood" v={a.likelihood}/></div>
                <div style={{ textAlign:"right", minWidth:150 }}>
                  <div style={{ fontFamily:F.ui, fontSize:13.5, fontWeight:700, color:C.gold }}>{RM(a.capacity*1000)}–{RM(a.capacity*1000*3)}</div>
                  <Tag color={a.capacity>300?C.gold:C.muted} soft={a.capacity>300?C.goldSoft:C.line2}>{a.capacity>300?"High capacity":a.capacity>120?"Medium":"Low"}</Tag>
                </div>
                <Btn kind="soft" sm icon={Mail}>Engage</Btn>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

/* ============================== UNIVERSITY: ALERTS ============================== */
function Alerts() {
  const meta = {
    job_change:{c:C.emerald, s:C.emeraldSoft, i:Briefcase, l:"Job change"},
    donor_prospect:{c:C.gold, s:C.goldSoft, i:DollarSign, l:"Donor prospect"},
    verification:{c:C.violet, s:C.violetSoft, i:ShieldCheck, l:"Verification"},
    data_quality:{c:C.amber, s:C.amberSoft, i:CircleAlert, l:"Data quality"},
    system:{c:C.muted, s:C.line2, i:Activity, l:"System"},
  };
  const [f,setF]=useState("all");
  const list = ALERTS.filter(a=>f==="all"||a.type===f);
  return (
    <div className="ai-fade">
      <PageHead title="Alerts" sub="Automatic notifications from the data pipeline"/>
      <div style={{ display:"flex", gap:8, marginBottom:14, flexWrap:"wrap" }}>
        {[["all","All"],["job_change","Job changes"],["donor_prospect","Donor prospects"],["verification","Verification"],["data_quality","Data quality"]].map(([k,l])=>(
          <button key={k} onClick={()=>setF(k)} className="navitem" style={{ border:`1px solid ${f===k?C.sapphire:C.line}`, cursor:"pointer", borderRadius:99,
            padding:"7px 14px", fontFamily:F.ui, fontSize:12.5, fontWeight:600, background: f===k?C.sapphireSoft:"#fff", color: f===k?C.sapphireDk:C.muted }}>{l}</button>
        ))}
      </div>
      {list.map(a=>{ const m=meta[a.type]; const I=m.i; return (
        <Card key={a.id} className="lift ai-row" style={{ display:"flex", alignItems:"center", gap:14, marginBottom:10, borderLeft:`3px solid ${m.c}` }} pad={16}>
          <div style={{ width:38, height:38, borderRadius:10, background:m.s, display:"grid", placeItems:"center", flex:"none" }}><I size={18} color={m.c}/></div>
          <div style={{ flex:1 }}>
            <div style={{ display:"flex", alignItems:"center", gap:8 }}>
              <span style={{ fontFamily:F.ui, fontSize:14, fontWeight:600, color:C.text }}>{a.who}</span>
              <Tag color={m.c} soft={m.s}>{m.l}</Tag>
              {a.pri==="high" && <Tag color={C.red} soft={C.redSoft}>High priority</Tag>}
            </div>
            <div style={{ fontFamily:F.ui, fontSize:13, color:C.muted, marginTop:2 }}>{a.detail}</div>
          </div>
          <span style={{ fontFamily:F.mono, fontSize:11.5, color:C.muted }}>{a.at}</span>
        </Card>
      );})}
    </div>
  );
}

/* ============================== UNIVERSITY: REPORTS + SETTINGS ============================== */
function Reports() {
  const rs = [
    { t:"Graduate Employability — 2024 cohort", d:"Employment rate, sector spread, time-to-employment", fmt:"PDF" },
    { t:"Industry Distribution Report", d:"Where UTM alumni work, by sector and seniority", fmt:"XLSX" },
    { t:"Career Progression Summary", d:"Promotions and role changes detected this year", fmt:"PDF" },
    { t:"Donor Prospect Shortlist", d:"Top prospects by giving likelihood and capacity", fmt:"XLSX" },
    { t:"Accreditation Outcomes Pack", d:"MQA-ready graduate outcome evidence", fmt:"PDF" },
  ];
  return (
    <div className="ai-fade">
      <PageHead title="Reports" sub="Exportable alumni outcome reports"/>
      <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>
        {rs.map((r,i)=>(
          <Card key={i} className="lift" style={{ display:"flex", alignItems:"center", gap:14 }}>
            <div style={{ width:42, height:42, borderRadius:12, background:C.sapphireSoft, display:"grid", placeItems:"center", flex:"none" }}><FileText size={20} color={C.sapphireDk}/></div>
            <div style={{ flex:1 }}>
              <div style={{ fontFamily:F.ui, fontSize:14, fontWeight:600, color:C.text }}>{r.t}</div>
              <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.muted }}>{r.d}</div>
            </div>
            <Btn kind="ghost" sm icon={Download}>{r.fmt}</Btn>
          </Card>
        ))}
      </div>
    </div>
  );
}
function SettingsScreen({ tenant }) {
  return (
    <div className="ai-fade">
      <PageHead title="Settings" sub="University account management"/>
      <Card style={{ maxWidth:620 }}>
        <CardTitle icon={Building2}>Institution</CardTitle>
        <div style={{ display:"grid", gap:14 }}>
          <Field label="Institution name" value={tenant.name} onChange={()=>{}}/>
          <Field label="Primary contact" value={tenant.admin} onChange={()=>{}}/>
          <Field label="Contact email" value={tenant.email} onChange={()=>{}}/>
          <div style={{ display:"flex", gap:12 }}>
            <MiniStat label="Subscription" value="Active" color={C.emerald}/>
            <MiniStat label="Member since" value={tenant.since}/>
            <MiniStat label="Seats used" value={`${tenant.users} / 5`}/>
          </div>
          <div><Btn icon={Check}>Save changes</Btn></div>
        </div>
      </Card>
    </div>
  );
}

/* ============================== SHARED HELPERS ============================== */
function PageHead({ title, sub, right }) {
  return <div style={{ display:"flex", alignItems:"flex-end", justifyContent:"space-between", marginBottom:20 }}>
    <div><h1 style={{ fontFamily:F.disp, fontSize:30, fontWeight:600, color:C.text, margin:0, letterSpacing:"-0.02em" }}>{title}</h1>
    <div style={{ fontFamily:F.ui, fontSize:13.5, color:C.muted, marginTop:3 }}>{sub}</div></div>{right}</div>;
}
function CardTitle({ children, icon:Icon }) {
  return <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:14 }}>
    {Icon && <Icon size={16} color={C.sapphire}/>}
    <span style={{ fontFamily:F.ui, fontSize:13.5, fontWeight:700, color:C.text, letterSpacing:"-0.01em" }}>{children}</span></div>;
}
function Dropdown({ value, onChange, options, icon:Icon }) {
  const [open,setOpen]=useState(false);
  return <div style={{ position:"relative" }}>
    <button onClick={()=>setOpen(o=>!o)} style={{ display:"flex", alignItems:"center", gap:8, padding:"9px 12px",
      border:`1px solid ${C.line}`, borderRadius:10, background:"#fff", cursor:"pointer", fontFamily:F.ui, fontSize:13, color:C.text, fontWeight:500 }}>
      {Icon && <Icon size={14} color={C.muted}/>}{value}<ChevronDown size={14} color={C.muted}/></button>
    {open && <><div onClick={()=>setOpen(false)} style={{ position:"fixed", inset:0, zIndex:9 }}/>
      <div style={{ position:"absolute", top:"calc(100% + 4px)", left:0, zIndex:10, background:"#fff", border:`1px solid ${C.line}`,
        borderRadius:10, boxShadow:"0 8px 24px rgba(0,0,0,.1)", minWidth:160, padding:5, maxHeight:240, overflow:"auto" }}>
        {options.map(o=>(<button key={o} onClick={()=>{onChange(o);setOpen(false);}} className="navitem"
          style={{ display:"block", width:"100%", textAlign:"left", padding:"8px 10px", border:"none", background: o===value?C.sapphireSoft:"transparent",
            borderRadius:7, cursor:"pointer", fontFamily:F.ui, fontSize:13, color: o===value?C.sapphireDk:C.text, fontWeight: o===value?600:500 }}>{o}</button>))}
      </div></>}
  </div>;
}

/* ============================== SUPER ADMIN: CUSTOMERS ============================== */
function Customers() {
  const [tab,setTab]=useState("active"); const [pending,setPending]=useState(PENDING);
  const [invite,setInvite]=useState(false); const [toast,setToast]=useState(null);
  const fire = m => { setToast(m); setTimeout(()=>setToast(null),2600); };
  const decide = (id,ok)=>{ setPending(p=>p.filter(r=>r.id!==id)); fire(ok?"University approved — portal activated":"Registration request denied"); };
  return (
    <div className="ai-fade">
      <PageHead title="Manage customers" sub="Onboard and govern university tenants"
        right={<Btn icon={Send} onClick={()=>setInvite(true)}>Invite university</Btn>}/>
      <div style={{ display:"flex", gap:8, marginBottom:16 }}>
        {[["active",`Active (${TENANTS.length})`],["pending",`Pending (${pending.length})`]].map(([k,l])=>(
          <button key={k} onClick={()=>setTab(k)} style={{ cursor:"pointer", borderRadius:10, padding:"9px 16px",
            fontFamily:F.ui, fontSize:13, fontWeight:600, background: tab===k?C.inkPanel:"transparent",
            color: tab===k?C.inkText:C.inkMute, border:`1px solid ${tab===k?C.inkLine:"transparent"}` }}>{l}</button>
        ))}
      </div>
      {tab==="active" ? (
        <div style={{ display:"grid", gap:12 }}>
          {TENANTS.map(t=>(
            <Card key={t.id} dark className="lift" style={{ display:"flex", alignItems:"center", gap:16 }}>
              <div style={{ width:46, height:46, borderRadius:12, background:C.ink2, color:C.inkText, display:"grid", placeItems:"center",
                fontFamily:F.disp, fontWeight:700, fontSize:16, border:`1px solid ${C.inkLine}` }}>{t.short}</div>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:F.ui, fontSize:15, fontWeight:600, color:C.inkText }}>{t.name}</div>
                <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.inkMute }}>{t.admin} · {t.email}</div>
              </div>
              <DarkStat label="Alumni" value={t.alumni.toLocaleString()}/>
              <DarkStat label="Users" value={t.users}/>
              <DarkStat label="Last import" value={t.lastImport}/>
              <Tag color={C.emerald} soft="rgba(28,138,90,.18)">Active</Tag>
            </Card>
          ))}
        </div>
      ) : (
        <div style={{ display:"grid", gap:12 }}>
          {pending.length===0 && <Card dark style={{ textAlign:"center", padding:42, color:C.inkMute, fontFamily:F.ui }}>
            <Inbox size={28} style={{margin:"0 auto 10px"}}/>No pending requests.</Card>}
          {pending.map(r=>(
            <Card key={r.id} dark className="ai-row" style={{ display:"flex", alignItems:"center", gap:16 }}>
              <Avatar name={r.name}/>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:F.ui, fontSize:15, fontWeight:600, color:C.inkText }}>{r.institution}</div>
                <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.inkMute }}>{r.name} · {r.jobTitle} · {r.email}</div>
              </div>
              <span style={{ fontFamily:F.mono, fontSize:11.5, color:C.inkMute }}>{r.at}</span>
              <Btn kind="danger" sm icon={X} onClick={()=>decide(r.id,false)}>Deny</Btn>
              <Btn kind="primary" sm icon={Check} onClick={()=>decide(r.id,true)}>Approve</Btn>
            </Card>
          ))}
        </div>
      )}
      {invite && <InviteModal onClose={()=>setInvite(false)} onSent={()=>{setInvite(false);fire("Invitation sent — link valid for 20 minutes");}}/>}
      {toast && <Toast>{toast}</Toast>}
    </div>
  );
}
function DarkStat({ label, value }) {
  return <div style={{ textAlign:"right", minWidth:74 }}>
    <div style={{ fontFamily:F.ui, fontSize:11, color:C.inkMute, fontWeight:600 }}>{label}</div>
    <div style={{ fontFamily:F.mono, fontSize:13.5, color:C.inkText, fontWeight:500 }}>{value}</div></div>;
}
function InviteModal({ onClose, onSent }) {
  const [email,setEmail]=useState(""); const [org,setOrg]=useState("");
  return <div style={{ position:"fixed", inset:0, zIndex:60, display:"grid", placeItems:"center" }}>
    <div onClick={onClose} style={{ position:"absolute", inset:0, background:"rgba(8,12,22,.6)", backdropFilter:"blur(3px)" }} className="ai-fade"/>
    <div className="ai-fade" style={{ position:"relative", width:"min(460px,92vw)", background:C.inkPanel, border:`1px solid ${C.inkLine}`, borderRadius:18, padding:26 }}>
      <div style={{ display:"flex", alignItems:"center", gap:10, marginBottom:18 }}>
        <div style={{ width:38, height:38, borderRadius:10, background:C.ink2, display:"grid", placeItems:"center" }}><Send size={18} color={C.sapphire}/></div>
        <div><div style={{ fontFamily:F.disp, fontSize:20, color:C.inkText, fontWeight:600 }}>Invite a university</div>
        <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.inkMute }}>Sends a registration link valid for 20 minutes</div></div>
      </div>
      <div style={{ display:"grid", gap:14 }}>
        <DarkField label="Institution" value={org} onChange={setOrg} placeholder="Universiti Teknologi PETRONAS"/>
        <DarkField label="PIC email" value={email} onChange={setEmail} placeholder="name@university.edu.my"/>
        <div style={{ display:"flex", gap:10, justifyContent:"flex-end", marginTop:4 }}>
          <Btn kind="ghost" dark sm onClick={onClose}>Cancel</Btn>
          <Btn icon={Send} sm onClick={onSent}>Send invite</Btn>
        </div>
      </div>
    </div>
  </div>;
}
function DarkField({ label, value, onChange, placeholder }) {
  return <label style={{ display:"block" }}>
    <div style={{ fontFamily:F.ui, fontSize:12, fontWeight:600, color:C.inkMute, marginBottom:6 }}>{label}</div>
    <input value={value} onChange={e=>onChange(e.target.value)} placeholder={placeholder}
      style={{ width:"100%", fontFamily:F.ui, fontSize:14, color:C.inkText, padding:"10px 12px",
        border:`1px solid ${C.inkLine}`, borderRadius:10, outline:"none", background:C.ink2 }}/></label>;
}
function Toast({ children }) {
  return <div className="ai-row" style={{ position:"fixed", bottom:26, left:"50%", transform:"translateX(-50%)", zIndex:80,
    background:C.text, color:"#fff", fontFamily:F.ui, fontSize:13.5, fontWeight:500, padding:"12px 20px", borderRadius:12,
    boxShadow:"0 10px 30px rgba(0,0,0,.25)", display:"flex", alignItems:"center", gap:10 }}>
    <CircleCheck size={17} color={C.emerald}/>{children}</div>;
}

/* ============================== SUPER ADMIN: DATA IMPORT + PIPELINE ============================== */
const PIPELINE = [
  { k:"upload", label:"Upload & validate CSV", icon:Upload, note:"315 rows · schema OK" },
  { k:"llm", label:"LLM normalisation (OpenAI)", icon:Sparkles, note:"Extract employer · title · seniority · industry" },
  { k:"match", label:"L1 / L2 / L3 matching", icon:GitBranch, note:"LinkedIn → name+year → name" },
  { k:"write", label:"Insert / update / unchanged", icon:Database, note:"Tenant-scoped writes + career events" },
];
function DataImport() {
  const [tenant,setTenant]=useState("Universiti Teknologi Malaysia");
  const [stage,setStage]=useState(-1); // -1 idle, 0..3 running, 4 done
  const [stats,setStats]=useState(null);
  const timer = useRef(null);
  const run = ()=>{
    setStats(null); setStage(0);
    let s=0; clearInterval(timer.current);
    timer.current=setInterval(()=>{ s++; if(s>3){ clearInterval(timer.current);
      setStage(4); setStats({total:315, inserted:198, updated:87, unchanged:27, failed:3}); }
      else setStage(s); },1100);
  };
  useEffect(()=>()=>clearInterval(timer.current),[]);
  const running = stage>=0 && stage<4;
  return (
    <div className="ai-fade">
      <PageHead title="Data import" sub="Upload a scraper export — the pipeline runs automatically"/>
      <div style={{ display:"grid", gridTemplateColumns:"360px 1fr", gap:16 }}>
        <Card dark>
          <CardTitleDark icon={Building2}>Target university</CardTitleDark>
          <DarkDropdown value={tenant} onChange={setTenant} options={TENANTS.map(t=>t.name)}/>
          <div style={{ height:16 }}/>
          <CardTitleDark icon={Upload}>Source file</CardTitleDark>
          <div style={{ border:`1.5px dashed ${C.inkLine}`, borderRadius:14, padding:"28px 16px", textAlign:"center", background:C.ink2 }}>
            <Upload size={26} color={C.sapphire} style={{margin:"0 auto 8px"}}/>
            <div style={{ fontFamily:F.ui, fontSize:13, color:C.inkText, fontWeight:600 }}>alumni_export_may2026.csv</div>
            <div style={{ fontFamily:F.mono, fontSize:11.5, color:C.inkMute, marginTop:3 }}>2.4 MB · CSV · 315 rows</div>
          </div>
          <div style={{ height:16 }}/>
          <Btn icon={running?Activity:Zap} onClick={run} style={{ width:"100%", justifyContent:"center", opacity:running?.7:1 }}>
            {running?"Pipeline running…":stage===4?"Run again":"Run pipeline"}</Btn>
        </Card>

        <Card dark>
          <CardTitleDark icon={GitBranch}>Processing pipeline</CardTitleDark>
          <div style={{ display:"grid", gap:10 }}>
            {PIPELINE.map((p,i)=>{
              const I=p.icon; const active=stage===i; const done=stage>i;
              return (
                <div key={p.k} style={{ display:"flex", alignItems:"center", gap:14, padding:"14px 16px", borderRadius:14,
                  background: active?C.ink2:"transparent", border:`1px solid ${active?C.sapphire:C.inkLine}`, transition:"all .3s ease" }}>
                  <div className={active?"ai-pulse":""} style={{ width:38, height:38, borderRadius:10, flex:"none", display:"grid", placeItems:"center",
                    background: done?"rgba(28,138,90,.18)":active?"rgba(45,75,196,.2)":C.ink2,
                    color: done?C.emerald:active?C.sapphire:C.inkMute }}>
                    {done? <Check size={18}/> : <I size={18}/>}</div>
                  <div style={{ flex:1 }}>
                    <div style={{ fontFamily:F.ui, fontSize:13.5, fontWeight:600, color: stage>=i?C.inkText:C.inkMute }}>{p.label}</div>
                    <div style={{ fontFamily:F.ui, fontSize:12, color:C.inkMute }}>{p.note}</div>
                  </div>
                  {done && <Tag color={C.emerald} soft="rgba(28,138,90,.18)">Done</Tag>}
                  {active && <Tag color={C.sapphire} soft="rgba(45,75,196,.2)">Running</Tag>}
                </div>
              );
            })}
          </div>
          {stats && (
            <div className="ai-fade" style={{ marginTop:16, padding:18, background:C.ink2, borderRadius:14, border:`1px solid ${C.inkLine}` }}>
              <div style={{ fontFamily:F.ui, fontSize:13, fontWeight:700, color:C.inkText, marginBottom:12 }}>Import batch B-0292 complete</div>
              <div style={{ display:"flex", gap:10 }}>
                {[["Total",stats.total,C.inkText],["New",stats.inserted,C.sapphire],["Updated",stats.updated,C.emerald],["Unchanged",stats.unchanged,C.inkMute],["Failed",stats.failed,C.red]].map(([l,v,c])=>(
                  <div key={l} style={{ flex:1, textAlign:"center", padding:"10px 4px", background:C.inkPanel, borderRadius:10 }}>
                    <div style={{ fontFamily:F.disp, fontSize:24, fontWeight:600, color:c }}>{v}</div>
                    <div style={{ fontFamily:F.ui, fontSize:11, color:C.inkMute, fontWeight:600 }}>{l}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
function CardTitleDark({ children, icon:Icon }) {
  return <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:12 }}>
    {Icon && <Icon size={15} color={C.sapphire}/>}
    <span style={{ fontFamily:F.ui, fontSize:12.5, fontWeight:700, color:C.inkText, letterSpacing:"0.02em", textTransform:"uppercase" }}>{children}</span></div>;
}
function DarkDropdown({ value, onChange, options }) {
  const [open,setOpen]=useState(false);
  return <div style={{ position:"relative" }}>
    <button onClick={()=>setOpen(o=>!o)} style={{ width:"100%", display:"flex", alignItems:"center", justifyContent:"space-between",
      padding:"10px 12px", border:`1px solid ${C.inkLine}`, borderRadius:10, background:C.ink2, cursor:"pointer", fontFamily:F.ui, fontSize:13.5, color:C.inkText }}>
      {value}<ChevronDown size={15} color={C.inkMute}/></button>
    {open && <><div onClick={()=>setOpen(false)} style={{ position:"fixed", inset:0, zIndex:9 }}/>
      <div style={{ position:"absolute", top:"calc(100% + 4px)", left:0, right:0, zIndex:10, background:C.inkPanel, border:`1px solid ${C.inkLine}`, borderRadius:10, padding:5 }}>
        {options.map(o=>(<button key={o} onClick={()=>{onChange(o);setOpen(false);}}
          style={{ display:"block", width:"100%", textAlign:"left", padding:"9px 10px", border:"none", background: o===value?C.ink2:"transparent",
            borderRadius:7, cursor:"pointer", fontFamily:F.ui, fontSize:13, color:C.inkText }}>{o}</button>))}
      </div></>}
  </div>;
}

/* ============================== SUPER ADMIN: PERMISSIONS ============================== */
function Permissions() {
  const [tenant,setTenant]=useState("Universiti Teknologi Malaysia");
  const [cats,setCats]=useState(()=>JSON.parse(JSON.stringify(PERM_CATS)));
  const [toast,setToast]=useState(null);
  const toggle=(ci,fi)=>{ setCats(cs=>{ const n=JSON.parse(JSON.stringify(cs)); n[ci].fields[fi].on=!n[ci].fields[fi].on; return n;});
    setToast("Permission updated"); setTimeout(()=>setToast(null),1800); };
  const on = cats.reduce((s,c)=>s+c.fields.filter(f=>f.on).length,0);
  const total = cats.reduce((s,c)=>s+c.fields.length,0);
  const reset=()=>{ setCats(JSON.parse(JSON.stringify(PERM_CATS))); setToast("Permissions reset to default"); setTimeout(()=>setToast(null),1800); };
  return (
    <div className="ai-fade">
      <PageHead title="Data permissions" sub={`Per-tenant control over ${total} data fields across 7 categories`}
        right={<Btn kind="ghost" dark sm onClick={reset}>Reset to default</Btn>}/>
      <Card dark style={{ marginBottom:16, display:"flex", alignItems:"center", gap:16 }}>
        <div style={{ flex:1, maxWidth:380 }}><DarkDropdown value={tenant} onChange={setTenant} options={TENANTS.map(t=>t.name)}/></div>
        <DarkStat label="Enabled" value={`${on} / ${total}`}/>
        <div style={{ flex:1, maxWidth:280 }}>
          <div style={{ height:8, borderRadius:99, background:C.ink2 }}>
            <div style={{ width:`${on/total*100}%`, height:"100%", borderRadius:99, background:`linear-gradient(90deg,${C.sapphire},${C.emerald})` }}/></div>
        </div>
      </Card>
      <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:14 }}>
        {cats.map((c,ci)=>(
          <Card key={c.cat} dark>
            <div style={{ fontFamily:F.ui, fontSize:13, fontWeight:700, color:C.inkText, marginBottom:12,
              paddingBottom:10, borderBottom:`1px solid ${C.inkLine}` }}>{c.cat}</div>
            {c.fields.map((f,fi)=>(
              <div key={f.k} style={{ display:"flex", alignItems:"center", gap:10, padding:"8px 0" }}>
                <span style={{ flex:1, fontFamily:F.ui, fontSize:13, color: f.on?C.inkText:C.inkMute }}>{f.label}</span>
                <Toggle on={f.on} onClick={()=>toggle(ci,fi)}/>
              </div>
            ))}
          </Card>
        ))}
      </div>
      {toast && <Toast>{toast}</Toast>}
    </div>
  );
}

/* ============================== SHELLS ============================== */
function NavItem({ icon:Icon, label, active, onClick, dark }) {
  return <button onClick={onClick} className="navitem" style={{ display:"flex", alignItems:"center", gap:11, width:"100%",
    padding:"10px 13px", border:"none", borderRadius:11, cursor:"pointer", textAlign:"left", marginBottom:3,
    background: active?(dark?C.ink2:C.sapphireSoft):"transparent",
    color: active?(dark?C.inkText:C.sapphireDk):(dark?C.inkMute:C.muted),
    fontFamily:F.ui, fontSize:13.5, fontWeight: active?600:500 }}>
    <Icon size={17} strokeWidth={active?2.4:2}/>{label}
    {active && <span style={{ marginLeft:"auto", width:5, height:5, borderRadius:99, background: dark?C.sapphire:C.sapphire }}/>}
  </button>;
}
function Wordmark({ dark, sub }) {
  return <div style={{ display:"flex", alignItems:"center", gap:10 }}>
    <div style={{ width:34, height:34, borderRadius:10, background: dark?C.sapphire:C.text, display:"grid", placeItems:"center", flex:"none" }}>
      <GitBranch size={18} color="#fff"/></div>
    <div><div style={{ fontFamily:F.disp, fontSize:19, fontWeight:600, color: dark?C.inkText:C.text, letterSpacing:"-0.01em", lineHeight:1 }}>AlumIndex</div>
    {sub && <div style={{ fontFamily:F.mono, fontSize:9.5, color: dark?C.inkMute:C.muted, letterSpacing:"0.14em", textTransform:"uppercase", marginTop:2 }}>{sub}</div>}</div></div>;
}

function Shell({ dark, nav, view, setView, session, onLogout, children, tenantSwitch }) {
  return (
    <div style={{ display:"flex", height:"100%", background: dark?C.ink:C.bone }}>
      <aside style={{ width:248, flex:"none", background: dark?C.ink2:C.surface, borderRight:`1px solid ${dark?C.inkLine:C.line}`,
        display:"flex", flexDirection:"column", padding:18 }}>
        <div style={{ padding:"4px 6px 20px" }}><Wordmark dark={dark} sub={dark?"Operator":"University"}/></div>
        <div style={{ flex:1 }}>
          {nav.map(n=><NavItem key={n.k} icon={n.icon} label={n.label} active={view===n.k} onClick={()=>setView(n.k)} dark={dark}/>)}
        </div>
        {tenantSwitch}
        <div style={{ borderTop:`1px solid ${dark?C.inkLine:C.line}`, paddingTop:14, marginTop:8 }}>
          <div style={{ display:"flex", alignItems:"center", gap:10, padding:"6px 6px 12px" }}>
            <Avatar name={session.name} gold={dark}/>
            <div style={{ flex:1, minWidth:0 }}>
              <div style={{ fontFamily:F.ui, fontSize:12.5, fontWeight:600, color: dark?C.inkText:C.text, overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{session.name}</div>
              <div style={{ fontFamily:F.ui, fontSize:11, color: dark?C.inkMute:C.muted }}>{session.roleLabel}</div>
            </div>
          </div>
          <NavItem icon={LogOut} label="Sign out" onClick={onLogout} dark={dark}/>
        </div>
      </aside>
      <main style={{ flex:1, overflow:"auto", padding:"30px 36px" }}>
        <div style={{ maxWidth:1180, margin:"0 auto" }}>{children}</div>
      </main>
    </div>
  );
}

function SuperAdminApp({ session, onLogout }) {
  const [view,setView]=useState("customers");
  const nav=[{k:"customers",icon:Users,label:"Manage customers"},{k:"import",icon:Upload,label:"Data import"},{k:"perms",icon:Sliders,label:"Data permissions"}];
  return <Shell dark nav={nav} view={view} setView={setView} session={session} onLogout={onLogout}>
    {view==="customers" && <Customers/>}
    {view==="import" && <DataImport/>}
    {view==="perms" && <Permissions/>}
  </Shell>;
}

function UniversityApp({ session, onLogout }) {
  const [view,setView]=useState("dashboard");
  const [tenantId,setTenantId]=useState(session.tenantId);
  const tenant = TENANTS.find(t=>t.id===tenantId);
  const alumni = ALUMNI[tenantId];
  const perms = { donation_pred:true, salary:true, seniority:true }; // UTM flagship: net-worth enabled
  const ro = session.role==="readonly";
  const nav=[
    {k:"dashboard",icon:LayoutDashboard,label:"Dashboard"},
    {k:"alumni",icon:Users,label:"Alumni database"},
    {k:"donor",icon:DollarSign,label:"Donor insights"},
    {k:"alerts",icon:Bell,label:"Alerts"},
    {k:"reports",icon:FileText,label:"Reports"},
    {k:"audit",icon:ScrollText,label:"Audit log"},
    {k:"settings",icon:Settings,label:"Settings"},
  ];
  return <Shell nav={nav} view={view} setView={setView} session={session} onLogout={onLogout}>
    {ro && <div style={{ display:"inline-flex", alignItems:"center", gap:7, marginBottom:14, padding:"6px 12px", borderRadius:99,
      background:C.violetSoft, color:C.violet, fontFamily:F.ui, fontSize:12, fontWeight:600 }}><Eye size={13}/>Read-only access — management actions are hidden</div>}
    {view==="dashboard" && <Dashboard tenant={tenant} alumni={alumni}/>}
    {view==="alumni" && <AlumniDB alumni={alumni} perms={perms}/>}
    {view==="donor" && <Donor alumni={alumni} perms={perms}/>}
    {view==="alerts" && <Alerts/>}
    {view==="reports" && <Reports/>}
    {view==="audit" && <AuditLog/>}
    {view==="settings" && <SettingsScreen tenant={tenant}/>}
  </Shell>;
}

function AuditLog() {
  return <div className="ai-fade">
    <PageHead title="Audit log" sub="Every governed action, with actor and timestamp"/>
    <Card pad={0} style={{ overflow:"hidden" }}>
      {AUDIT.map(a=>(
        <div key={a.id} className="tdrow" style={{ display:"flex", alignItems:"center", gap:14, padding:"13px 18px", borderBottom:`1px solid ${C.line2}` }}>
          <div style={{ width:34, height:34, borderRadius:9, background:C.line2, display:"grid", placeItems:"center", flex:"none" }}><ScrollText size={16} color={C.muted}/></div>
          <div style={{ flex:1 }}>
            <div style={{ fontFamily:F.mono, fontSize:12, fontWeight:600, color:C.sapphireDk }}>{a.action}</div>
            <div style={{ fontFamily:F.ui, fontSize:13, color:C.text }}>{a.detail}</div>
          </div>
          <div style={{ textAlign:"right" }}>
            <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.text, fontWeight:500 }}>{a.actor}</div>
            <div style={{ fontFamily:F.mono, fontSize:11, color:C.muted }}>{a.at}</div>
          </div>
        </div>
      ))}
    </Card>
  </div>;
}

/* ============================== LOGIN ============================== */
function Login({ onLogin }) {
  const roles=[
    { role:"superadmin", name:"Amr Alwaeli", roleLabel:"Platform operator", email:"amralwaeli9@gmail.com", desc:"Onboard universities · import data · set permissions", icon:Shield, accent:C.sapphire },
    { role:"admin", name:"Norhafisah Zakaria", roleLabel:"University admin · UTM", tenantId:"t1", email:"norhafisah@utm.my", desc:"Full access to UTM alumni intelligence", icon:UserCog, accent:C.gold },
    { role:"readonly", name:"Siti Aminah", roleLabel:"Read-only · UTM", tenantId:"t1", email:"siti@utm.my", desc:"View dashboards, search, reports", icon:Eye, accent:C.violet },
  ];
  return (
    <div style={{ height:"100%", display:"grid", gridTemplateColumns:"1.05fr 1fr", background:C.ink }}>
      {/* left hero — trajectory motif */}
      <div style={{ position:"relative", padding:"54px 56px", overflow:"hidden", display:"flex", flexDirection:"column" }}>
        <Wordmark dark sub="Career intelligence"/>
        <div style={{ marginTop:"auto", marginBottom:0, position:"relative", zIndex:2 }}>
          <div style={{ fontFamily:F.disp, fontSize:46, fontWeight:600, color:C.inkText, lineHeight:1.08, letterSpacing:"-0.025em", maxWidth:520 }}>
            Fragmented alumni records,<br/>resolved into a living<br/><span style={{ color:C.gold }}>career trajectory.</span></div>
          <div style={{ fontFamily:F.ui, fontSize:15, color:C.inkMute, marginTop:18, maxWidth:460, lineHeight:1.6 }}>
            A multi-tenant platform that normalises, matches and visualises graduate careers — the capability universities were quoted up to RM 134,817 a year for, at zero licensing cost.</div>
          {/* mini trajectory */}
          <div style={{ display:"flex", alignItems:"flex-end", gap:0, marginTop:40, maxWidth:520 }}>
            {[1,2,3,4,5].map((n,i)=>(
              <div key={i} style={{ flex:1, display:"flex", flexDirection:"column", alignItems:"center", position:"relative" }}>
                {i>0 && <div style={{ position:"absolute", left:"-50%", bottom: 8+i*14, width:"100%", height:2, background:`linear-gradient(90deg,${C.inkLine},${C.sapphire})` }}/>}
                <div style={{ width: i===4?13:9, height:i===4?13:9, borderRadius:99, background:i===4?C.gold:C.sapphire, marginBottom:6, boxShadow:`0 0 0 4px ${i===4?"rgba(169,121,31,.18)":"rgba(45,75,196,.16)"}` }}/>
                <div style={{ width:2, height:8+i*14, background:`linear-gradient(${C.sapphire},${C.inkLine})` }}/>
              </div>
            ))}
          </div>
        </div>
        <div style={{ position:"absolute", right:-120, top:-80, width:380, height:380, borderRadius:999, background:"radial-gradient(circle, rgba(45,75,196,.18), transparent 70%)" }}/>
        <div style={{ position:"absolute", right:40, bottom:-60, width:260, height:260, borderRadius:999, background:"radial-gradient(circle, rgba(169,121,31,.14), transparent 70%)" }}/>
      </div>
      {/* right — role picker */}
      <div style={{ background:C.bone, padding:"54px 56px", display:"flex", flexDirection:"column", justifyContent:"center" }}>
        <div style={{ fontFamily:F.disp, fontSize:26, fontWeight:600, color:C.text, letterSpacing:"-0.02em" }}>Sign in</div>
        <div style={{ fontFamily:F.ui, fontSize:14, color:C.muted, marginTop:4, marginBottom:26 }}>Choose a role to explore the platform.</div>
        <div style={{ display:"grid", gap:12 }}>
          {roles.map(r=>{ const I=r.icon; return (
            <button key={r.role} onClick={()=>onLogin(r)} className="lift" style={{ display:"flex", alignItems:"center", gap:14,
              padding:"16px 18px", background:"#fff", border:`1px solid ${C.line}`, borderRadius:14, cursor:"pointer", textAlign:"left" }}>
              <div style={{ width:44, height:44, borderRadius:12, background:`${r.accent}14`, display:"grid", placeItems:"center", flex:"none" }}><I size={21} color={r.accent}/></div>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:F.ui, fontSize:14.5, fontWeight:600, color:C.text }}>{r.roleLabel}</div>
                <div style={{ fontFamily:F.ui, fontSize:12.5, color:C.muted }}>{r.desc}</div>
              </div>
              <ArrowRight size={18} color={r.accent}/>
            </button>
          );})}
        </div>
        <div style={{ fontFamily:F.mono, fontSize:11, color:C.muted, marginTop:24, lineHeight:1.7 }}>
          React · TypeScript · Tailwind · shadcn/ui · Spring Boot · Supabase · OpenAI<br/>
          Frontend realisation · interactive prototype · simulated pipeline</div>
      </div>
    </div>
  );
}

/* ============================== ROOT ============================== */
export default function App() {
  const [session,setSession]=useState(null);
  return (
    <div style={{ position:"fixed", inset:0, fontFamily:F.ui, color:C.text, background:C.ink }}>
      <GlobalStyle/>
      {!session && <Login onLogin={setSession}/>}
      {session?.role==="superadmin" && <SuperAdminApp session={session} onLogout={()=>setSession(null)}/>}
      {session && session.role!=="superadmin" && <UniversityApp session={session} onLogout={()=>setSession(null)}/>}
    </div>
  );
}
