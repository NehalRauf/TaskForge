import { useState, useEffect, useRef, useCallback } from "react"
import { Client } from "@stomp/stompjs"
import SockJS from "sockjs-client"

const API = ""
const WS_URL = "/ws"

const S_COLOR = { QUEUED:"#7b8cde", RUNNING:"#ffd84d", COMPLETED:"#00e5a0", FAILED:"#ff5a5a", DEAD_LETTER:"#c084fc" }
const Q_COLOR = { HIGH:"#ff5a5a", NORMAL:"#ffd84d", LOW:"#7b8cde" }
const S_ICON  = { QUEUED:"○", RUNNING:"◉", COMPLETED:"✓", FAILED:"✗", DEAD_LETTER:"☠" }

const sel = {
  width:"100%", background:"#0a1628", border:"1px solid #1e3a5f",
  color:"#e2e8f0", padding:"8px 10px", borderRadius:5,
  fontFamily:'"JetBrains Mono",monospace', fontSize:11, outline:"none"
}

function Dot({ color }) {
  return <span style={{ display:"inline-block", width:7, height:7, borderRadius:"50%",
    background:color, boxShadow:`0 0 6px ${color}`, animation:"pulse 1.4s infinite", flexShrink:0 }} />
}

export default function App() {
  const [jobs, setJobs]       = useState([])
  const [stats, setStats]     = useState({})
  const [depths, setDepths]   = useState({})
  const [tab, setTab]         = useState("all")
  const [ws, setWs]           = useState("connecting")
  const [feed, setFeed]       = useState([])
  const [toast, setToast]     = useState(null)
  const [busy, setBusy]       = useState(false)
  const [err, setErr]         = useState(null)
  const form = useRef({ type:"email", priority:"NORMAL", retries:"3" })

  const showToast = useCallback(msg => {
    setToast(msg); setTimeout(() => setToast(null), 3000)
  }, [])

  const addFeed = useCallback((eventType, job) => {
    const label = {
      JOB_CREATED:`New ${job.type} job (${job.queuePriority})`,
      JOB_STARTED:`${job.type} picked up by ${job.assignedWorker||"worker"}`,
      JOB_COMPLETED:`${job.type} done in ${job.durationMs?(job.durationMs/1000).toFixed(1)+"s":"—"}`,
      JOB_FAILED:`${job.type} failed (${job.retryCount}/${job.maxRetries})`,
      JOB_DLQ:`${job.type} → dead letter queue`,
      JOB_RETRIED:`Retry triggered for ${job.type}`,
    }
    const color = {
      JOB_CREATED:"#7b8cde", JOB_STARTED:"#ffd84d", JOB_COMPLETED:"#00e5a0",
      JOB_FAILED:"#ff5a5a", JOB_DLQ:"#c084fc", JOB_RETRIED:"#ffd84d"
    }
    setFeed(p => [{ msg:label[eventType]||eventType, time:new Date().toLocaleTimeString(), color:color[eventType]||"#64748b" }, ...p].slice(0,30))
  }, [])

  const loadStats = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/v1/jobs/stats`)
      if (!r.ok) return
      const d = await r.json()
      setStats(d.jobCounts||{}); setDepths(d.queueDepths||{})
    } catch {}
  }, [])

  const loadJobs = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/v1/jobs?size=100`)
      if (!r.ok) throw new Error(await r.text())
      const d = await r.json()
      setJobs(d.jobs||[]); setErr(null)
    } catch(e) { setErr("Cannot reach API: "+e.message) }
  }, [])

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 4000,
      onConnect: () => {
        setWs("connected")
        client.subscribe("/topic/jobs", msg => {
          const ev = JSON.parse(msg.body)
          setJobs(p => {
            const i = p.findIndex(j => j.id === ev.job.id)
            if (i >= 0) { const n=[...p]; n[i]=ev.job; return n }
            return [ev.job,...p]
          })
          addFeed(ev.eventType, ev.job)
          loadStats()
          showToast(ev.eventType.replace(/_/g," ")+": "+ev.job.type)
        })
      },
      onDisconnect: () => setWs("disconnected"),
      onStompError: () => setWs("disconnected"),
    })
    client.activate()
    return () => client.deactivate()
  }, [addFeed, loadStats, showToast])

  useEffect(() => {
    loadJobs(); loadStats()
    const t = setInterval(loadStats, 6000)
    return () => clearInterval(t)
  }, [loadJobs, loadStats])

  const submitJob = async () => {
    setBusy(true); setErr(null)
    try {
      const r = await fetch(`${API}/api/v1/jobs`, {
        method:"POST", headers:{"Content-Type":"application/json"},
        body: JSON.stringify({
          type: form.current.type,
          queuePriority: form.current.priority,
          payload: JSON.stringify({ source:"dashboard", at: new Date().toISOString() }),
          maxRetries: parseInt(form.current.retries||"3")
        })
      })
      if (!r.ok) { const e=await r.json(); throw new Error(e.error||"Failed") }
      showToast("Job submitted!")
    } catch(e) { setErr("Submit failed: "+e.message) }
    finally { setBusy(false) }
  }

  const retryJob = async id => {
    try {
      const r = await fetch(`${API}/api/v1/jobs/${id}/retry`, { method:"POST" })
      if (!r.ok) throw new Error(await r.text())
      showToast("Retry queued"); loadJobs(); loadStats()
    } catch(e) { setErr("Retry failed: "+e.message) }
  }

  const filtered = tab === "all" ? jobs : jobs.filter(j => j.status === tab)
  const total = Object.values(stats).reduce((a,b) => a+b, 0)
  const wsColor = ws === "connected" ? "#00e5a0" : ws === "disconnected" ? "#ff5a5a" : "#ffd84d"

  return (
    <div style={{ minHeight:"100vh", background:"#060d1a", color:"#e2e8f0", fontFamily:'"JetBrains Mono",monospace' }}>

      {/* Header */}
      <div style={{ background:"#080f1e", borderBottom:"1px solid #0f2a45", padding:"13px 28px", display:"flex", alignItems:"center", justifyContent:"space-between" }}>
        <div style={{ display:"flex", alignItems:"center", gap:12 }}>
          <div style={{ width:34, height:34, borderRadius:8, background:"linear-gradient(135deg,#1e3a8a,#7b8cde)", display:"flex", alignItems:"center", justifyContent:"center", fontSize:16 }}>⚙</div>
          <div>
            <div style={{ fontFamily:'"Syne",sans-serif', fontWeight:800, fontSize:16, letterSpacing:1 }}>TASKFORGE</div>
            <div style={{ fontSize:9, color:"#334155", letterSpacing:2 }}>DISTRIBUTED JOB QUEUE SYSTEM</div>
          </div>
        </div>
        <div style={{ display:"flex", alignItems:"center", gap:8, fontSize:10, color:wsColor }}>
          <Dot color={wsColor} />
          {ws === "connected" ? "Live" : ws === "disconnected" ? "Disconnected" : "Connecting…"}
        </div>
      </div>

      {/* Metrics */}
      <div style={{ display:"grid", gridTemplateColumns:"repeat(5,1fr)", borderBottom:"1px solid #0f2a45" }}>
        {[
          { label:"TOTAL",     val:total||"—",          color:"#e2e8f0", sub:"all time"        },
          { label:"QUEUED",    val:stats.queued,         color:"#7b8cde", sub:"waiting"         },
          { label:"RUNNING",   val:stats.running,        color:"#ffd84d", sub:"active"          },
          { label:"COMPLETED", val:stats.completed,      color:"#00e5a0", sub:"successful"      },
          { label:"DEAD LETTER",val:stats.dlq,           color:"#c084fc", sub:"permanently failed"},
        ].map(m => (
          <div key={m.label} style={{ background:"#080f1e", padding:"16px 22px", borderRight:"1px solid #0f2a45" }}>
            <div style={{ fontSize:9, color:"#334155", letterSpacing:2, marginBottom:6 }}>{m.label}</div>
            <div style={{ fontFamily:'"Syne",sans-serif', fontWeight:800, fontSize:26, color:m.color, lineHeight:1 }}>{m.val??'—'}</div>
            <div style={{ fontSize:9, color:"#64748b", marginTop:4 }}>{m.sub}</div>
          </div>
        ))}
      </div>

      <div style={{ display:"grid", gridTemplateColumns:"1fr 280px", minHeight:"calc(100vh - 118px)" }}>

        {/* Main */}
        <div style={{ borderRight:"1px solid #0f2a45", padding:"22px 26px", overflowY:"auto" }}>

          {/* Submit */}
          <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr 110px auto", gap:10, marginBottom:16, alignItems:"end" }}>
            <div>
              <div style={{ fontSize:9, color:"#334155", letterSpacing:1, marginBottom:5 }}>JOB TYPE</div>
              <select defaultValue="email" style={sel} onChange={e => form.current.type = e.target.value}>
                {["email","video","report","thumbnail","export","notify","cleanup"].map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <div style={{ fontSize:9, color:"#334155", letterSpacing:1, marginBottom:5 }}>PRIORITY</div>
              <select defaultValue="NORMAL" style={sel} onChange={e => form.current.priority = e.target.value}>
                {["HIGH","NORMAL","LOW"].map(p => <option key={p}>{p}</option>)}
              </select>
            </div>
            <div>
              <div style={{ fontSize:9, color:"#334155", letterSpacing:1, marginBottom:5 }}>MAX RETRIES</div>
              <input type="number" defaultValue={3} min={0} max={10} style={sel} onChange={e => form.current.retries = e.target.value} />
            </div>
            <div>
              <div style={{ fontSize:9, color:"#334155", letterSpacing:1, marginBottom:5 }}>&nbsp;</div>
              <button onClick={submitJob} disabled={busy} style={{
                padding:"8px 18px", background:busy?"#334155":"#7b8cde", border:"none", borderRadius:5,
                color:"white", fontFamily:'"JetBrains Mono",monospace', fontSize:11, fontWeight:600,
                letterSpacing:1, cursor:busy?"not-allowed":"pointer", whiteSpace:"nowrap"
              }}>
                {busy ? "SUBMITTING…" : "SUBMIT JOB"}
              </button>
            </div>
          </div>

          {err && <div style={{ background:"#1a0a0a", border:"1px solid #ff5a5a", borderRadius:6, padding:"9px 14px", color:"#ff5a5a", fontSize:10, marginBottom:14 }}>{err}</div>}

          {/* Tabs */}
          <div style={{ display:"flex", gap:4, marginBottom:14, borderBottom:"1px solid #0f2a45", paddingBottom:10 }}>
            {[["all","ALL"],["RUNNING","RUNNING"],["FAILED","FAILED"],["DEAD_LETTER","DEAD LETTER"]].map(([k,l]) => (
              <button key={k} onClick={() => setTab(k)} style={{
                background:tab===k?"#1e293b":"none", border:"none", cursor:"pointer",
                padding:"6px 14px", borderRadius:4, fontFamily:'"JetBrains Mono",monospace',
                fontSize:10, letterSpacing:1, color:tab===k?"#ffd84d":"#475569"
              }}>{l}</button>
            ))}
            <span style={{ marginLeft:"auto", fontSize:10, color:"#334155", alignSelf:"center" }}>{filtered.length} jobs</span>
          </div>

          {/* Table header */}
          <div style={{ display:"grid", gridTemplateColumns:"130px 90px 110px 70px 100px 80px 60px 80px", gap:8, padding:"5px 10px", fontSize:9, color:"#334155", letterSpacing:1.5, borderBottom:"1px solid #0f2a45", marginBottom:2 }}>
            {["JOB ID","TYPE","STATUS","PRIORITY","WORKER","DURATION","RETRIES",""].map((h,i) => <span key={i}>{h}</span>)}
          </div>

          {filtered.length === 0
            ? <div style={{ color:"#475569", fontSize:11, padding:30, textAlign:"center" }}>No jobs</div>
            : filtered.map(job => {
                const dur = job.durationMs ? `${(job.durationMs/1000).toFixed(1)}s` : job.status==="RUNNING" ? "…" : "—"
                const canRetry = job.status==="FAILED" || job.status==="DEAD_LETTER"
                return (
                  <div key={job.id} style={{ display:"grid", gridTemplateColumns:"130px 90px 110px 70px 100px 80px 60px 80px", gap:8, padding:"9px 10px", fontSize:11, borderBottom:"1px solid #080f1e", borderLeft:`2px solid ${S_COLOR[job.status]||"transparent"}` }}>
                    <span style={{ color:"#7b8cde", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }} title={job.id}>{job.id.slice(0,8)}…</span>
                    <span style={{ color:"#94a3b8" }}>{job.type}</span>
                    <span style={{ display:"flex", alignItems:"center", gap:5, color:S_COLOR[job.status] }}>
                      {job.status==="RUNNING" && <Dot color="#ffd84d" />}
                      <span style={{ fontSize:10 }}>{S_ICON[job.status]} {job.status}</span>
                    </span>
                    <span><span style={{ fontSize:9, padding:"1px 6px", borderRadius:3, border:`1px solid ${Q_COLOR[job.queuePriority]}`, color:Q_COLOR[job.queuePriority] }}>{job.queuePriority}</span></span>
                    <span style={{ color:"#64748b", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap", fontSize:10 }}>{job.assignedWorker||"—"}</span>
                    <span style={{ color:"#475569" }}>{dur}</span>
                    <span style={{ color:job.retryCount>0?"#ff5a5a":"#334155" }}>{job.retryCount}/{job.maxRetries}</span>
                    <span>{canRetry && <button onClick={() => retryJob(job.id)} style={{ background:"none", border:"1px solid #ffd84d", color:"#ffd84d", fontSize:9, padding:"3px 8px", borderRadius:3, cursor:"pointer", fontFamily:'"JetBrains Mono",monospace' }}>RETRY</button>}</span>
                  </div>
                )
              })
          }
        </div>

        {/* Sidebar */}
        <div style={{ padding:20, overflowY:"auto" }}>
          <div style={{ fontSize:9, color:"#334155", letterSpacing:2, marginBottom:14 }}>QUEUE DEPTH (REDIS)</div>
          {[
            { label:"HIGH PRIORITY", key:"high",   max:50,  color:"#ff5a5a" },
            { label:"NORMAL",        key:"normal",  max:100, color:"#ffd84d" },
            { label:"LOW PRIORITY",  key:"low",     max:200, color:"#7b8cde" },
            { label:"DEAD LETTER",   key:"dlq",     max:50,  color:"#c084fc" },
          ].map(q => (
            <div key={q.key} style={{ marginBottom:16 }}>
              <div style={{ display:"flex", justifyContent:"space-between", fontSize:9, marginBottom:5 }}>
                <span style={{ color:q.color }}>{q.label}</span>
                <span style={{ color:"#64748b" }}>{depths[q.key]||0}</span>
              </div>
              <div style={{ height:5, background:"#0a1628", borderRadius:3, overflow:"hidden" }}>
                <div style={{ height:"100%", width:`${Math.min(100,((depths[q.key]||0)/q.max)*100)}%`, background:q.color, borderRadius:3, transition:"width 0.5s ease", boxShadow:`0 0 8px ${q.color}55` }} />
              </div>
            </div>
          ))}

          <div style={{ height:1, background:"#0f2a45", margin:"18px 0" }} />

          <div style={{ fontSize:9, color:"#334155", letterSpacing:2, marginBottom:12 }}>LIVE ACTIVITY</div>
          {feed.length===0
            ? <div style={{ color:"#334155", fontSize:10 }}>Waiting for events…</div>
            : feed.map((a,i) => (
              <div key={i} style={{ display:"flex", gap:8, marginBottom:10, fontSize:10 }}>
                <div style={{ width:2, background:a.color, borderRadius:1, flexShrink:0 }} />
                <div>
                  <div style={{ color:"#94a3b8", lineHeight:1.4 }}>{a.msg}</div>
                  <div style={{ color:"#334155", fontSize:9, marginTop:2 }}>{a.time}</div>
                </div>
              </div>
            ))
          }
        </div>
      </div>

      {toast && (
        <div style={{ position:"fixed", bottom:20, right:20, background:"#0a1628", border:"1px solid #1e3a5f", borderRadius:8, padding:"11px 18px", fontSize:11, color:"#e2e8f0", zIndex:999, animation:"slideUp 0.3s ease" }}>
          {toast}
        </div>
      )}
    </div>
  )
}
