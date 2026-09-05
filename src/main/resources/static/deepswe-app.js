/* DeepSWE 排行榜前端渲染：DATA 来源 = 服务端 SSR 注入 or /top 接口 */
(function(){
  var BASE = (window.DEEPSWE_BASE)
    || "/apis/api.deep-swe-leaderboard.halo.run/v1alpha1/leaderboard";
  var DEFAULT_VIEW = (window.DEEPSWE_DEFAULT_VIEW) || "table";
  var hasStaticData = !!(window.DEEPSWE_DATA && window.DEEPSWE_DATA.rows);

  var COOLDOWN_MS = 15000;
  var app, toolbar, statusEl, contentEl, refreshBtn;
  var state = {
    view: DEFAULT_VIEW,
    size: 20,
    data: window.DEEPSWE_DATA || null,
    last: 0,
    timer: null
  };

  var VIEWS = [
    { value: "table", label: "经典表格" },
    { value: "bars", label: "横向条形榜" },
    { value: "podium", label: "领奖台" },
    { value: "grid", label: "卡片磁贴" },
    { value: "neon", label: "Neon 霓虹" }
  ];
  var SIZES = [[10,"10 名"],[20,"20 名"],[50,"50 名"]];

  function fmt(n){ if(n==null) return "-"; var x=Number(n); if(x>=1e4) return (x/1e3).toFixed(1)+"k"; return x.toLocaleString(); }
  function esc(s){ return String(s==null?"":s).replace(/[&<>"]/g,function(c){return{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c];}); }
  function effortRow(r){ return r.effort ? "<small>"+esc(r.effort)+"</small>" : ""; }
  function costCls(r){ return (r.cost!=null && r.cost>15) ? " costHigh" : ""; }
  function effTxt(r){ return r.effort ? " · "+esc(r.effort) : ""; }
  function cl(i){ return i===0 ? "n1" : i===1 ? "n2" : i===2 ? "n3" : "other"; }

  function renderTable(data){
    var t = "<div class='tableCard'><table class='tbl'><thead><tr>"+
      "<th>#</th><th>模型</th><th>Pass@1</th><th>成本</th><th>输出 Token</th><th>Agent 步数</th>"+
      "</tr></thead><tbody>";
    data.rows.forEach(function(r,i){
      var m = i===0 ? "<span class='medal gold'>1</span>" : i===1 ? "<span class='medal silver'>2</span>" : i===2 ? "<span class='medal bronze'>3</span>" : ""+(i+1);
      var bcl = i===0 ? " top1" : i===1 ? " top2" : i===2 ? " top3" : "";
      var pct = (r.passRatePct!=null?r.passRatePct:0);
      t += "<tr>"+
        "<td class='rank'>"+m+"</td>"+
        "<td class='model'>"+esc(r.displayName)+effortRow(r)+"</td>"+
        "<td><div style='display:flex;align-items:center;gap:8px'><div class='barWrap'><div class='bar"+bcl+"' style='width:"+pct+"%'></div></div><span class='num'>"+pct+"%</span></div></td>"+
        "<td class='num"+costCls(r)+"'>$"+((r.cost!=null?r.cost:0).toFixed(2))+"</td>"+
        "<td class='num'>"+fmt(r.outTok)+"</td>"+
        "<td class='num'>"+fmt(r.steps)+"</td>"+
        "</tr>";
    });
    return t + "</tbody></table></div>";
  }

  function renderBars(data){
    var h = "<div class='view-bars'>";
    data.rows.forEach(function(r,i){
      var pct = (r.passRatePct!=null?r.passRatePct:0);
      var bcl = i===0 ? " t1" : i===1 ? " t2" : i===2 ? " t3" : "";
      h += "<div class='barRow'>"+
        "<span class='bDot "+cl(i)+"'>"+(i+1)+"</span>"+
        "<div class='bName'>"+esc(r.displayName)+effortRow(r)+"</div>"+
        "<div class='bTrack'><div class='bFill"+bcl+"' style='width:"+pct+"%'></div></div>"+
        "<div class='bVal num'>"+pct+"%</div>"+
        "</div>";
    });
    return h + "</div>";
  }

  function renderPodium(data){
    var top = data.rows.slice(0,3);
    var order = [1,0,2];
    var labels = ["冠军","亚军","季军"];
    var classes = ["gold","silver","bronze"];
    var p = "<div class='podiumCard'><div class='podium'>";
    order.forEach(function(idx){
      var r = top[idx];
      if(!r) return;
      var pct = (r.passRatePct!=null?r.passRatePct:0);
      p += "<div class='pod "+classes[idx]+"'>"+
        "<small class='label'>"+labels[idx]+"</small>"+
        "<div class='pRank'>"+(idx+1)+"</div>"+
        "<div class='pName'>"+esc(r.displayName)+"</div>"+
        "<div class='pPct num'>"+pct+"%</div>"+
        "<div class='pSub'>cost $"+((r.cost!=null?r.cost:0).toFixed(2))+effTxt(r)+"</div>"+
        "</div>";
    });
    p += "</div><div class='restList'>";
    var k = 4;
    data.rows.slice(3).forEach(function(r){
      var n = k++;
      p += "<div class='reRow'>"+
        "<span class='reRank'>"+n+"</span>"+
        "<div class='reModel'>"+esc(r.displayName)+effortRow(r)+"</div>"+
        "<span class='num'>"+(r.passRatePct!=null?r.passRatePct:0)+"%</span>"+
        "<span class='num'>$"+((r.cost!=null?r.cost:0).toFixed(2))+"</span>"+
        "</div>";
    });
    return p + "</div></div>";
  }

  function renderGrid(data){
    var h = "<div class='view-grid'>";
    data.rows.forEach(function(r,i){
      var pct = (r.passRatePct!=null?r.passRatePct:0);
      h += "<div class='tile'>"+
        "<div class='gTop'><span class='tRank "+cl(i)+"'>"+(i+1)+"</span>"+
        "<div class='tName'>"+esc(r.displayName)+effortRow(r)+"</div></div>"+
        "<div class='tRingWrap'>"+
          "<div class='ring "+cl(i)+"' style='--p:"+pct+"'><span>"+pct+"%</span></div>"+
          "<div><div class='tPctTxt'>Pass@1</div><div class='tSub'>cost $"+((r.cost!=null?r.cost:0).toFixed(2))+"</div></div>"+
        "</div>"+
        "<div class='tFoot'><span>输出 "+fmt(r.outTok)+"</span><span>"+fmt(r.steps)+" 步</span></div>"+
        "</div>";
    });
    return h + "</div>";
  }

  function renderNeon(data){
    var h = "<div class='view-neon'>";
    data.rows.forEach(function(r,i){
      var pct = (r.passRatePct!=null?r.passRatePct:0);
      var glow = (i<3) ? " glow" : "";
      h += "<div class='ncard"+(i<3?" topN":"")+"'>"+
        "<span class='nrank'>"+(i+1)+"</span>"+
        "<div class='nname'>"+esc(r.displayName)+effortRow(r)+"</div>"+
        "<div class='nbar'><div class='fill"+glow+"' style='width:"+pct+"%'></div></div>"+
        "<span class='npct'>"+pct+"%</span>"+
        "</div>";
    });
    return h + "<div class='nfootOut'>Pass@1 · 名次 · 高亮为前三名</div></div>";
  }

  function renderView(){
    var data = state.data;
    if(!data || data.available !== true || !data.rows || !data.rows.length){
      contentEl.innerHTML = "<div class='ds-loading ds-err'>暂无数据</div>";
      return;
    }
    var v = state.view;
    if(v==="bars") contentEl.innerHTML = renderBars(data);
    else if(v==="podium") contentEl.innerHTML = renderPodium(data);
    else if(v==="grid") contentEl.innerHTML = renderGrid(data);
    else if(v==="neon") contentEl.innerHTML = renderNeon(data);
    else contentEl.innerHTML = renderTable(data);
  }

  function setMeta(d){
    var m = document.getElementById("dsMeta");
    if(!m) return;
    m.innerHTML = (d.available === true)
      ? "<span>任务数 <b>"+esc(d.nTasks)+"</b></span>"+
        "<span>数据源 <b>"+esc(d.source||"-")+"</b></span>"+
        "<span>生成于 <b>"+esc((d.generatedAt||"").replace("T"," ").slice(0,16)||"-")+"</b></span>"
      : "<span>任务数 <b>-</b></span>";
  }

  function setStatus(txt, spin){
    statusEl.innerHTML = (spin ? "<span class='ds-spin'></span> " : "") + esc(txt);
  }

  function buildToolbar(){
    toolbar.innerHTML = "";
    var vSel = document.createElement("select");
    vSel.className = "seek";
    VIEWS.forEach(function(v){
      var o = document.createElement("option");
      o.value = v.value; o.textContent = v.label;
      if(v.value === state.view) o.selected = true;
      vSel.appendChild(o);
    });
    vSel.addEventListener("change", function(){ state.view = vSel.value; renderView(); if(state.timer) clearInterval(state.timer); state.timer = null; });

    var sSel = document.createElement("select");
    sSel.className = "seek";
    sSel.id = "sizeSel";
    SIZES.forEach(function(s){
      var o = document.createElement("option");
      o.value = s[0]; o.textContent = s[1];
      if(s[0] === state.size) o.selected = true;
      sSel.appendChild(o);
    });
    sSel.addEventListener("change", function(){ state.size = parseInt(sSel.value,10); load(true); });

    refreshBtn = document.createElement("button");
    refreshBtn.id = "refreshBtn";
    refreshBtn.textContent = "刷新";
    refreshBtn.addEventListener("click", function(){ load(true); });

    statusEl = document.createElement("span");
    statusEl.className = "status";

    toolbar.appendChild(vSel);
    toolbar.appendChild(sSel);
    toolbar.appendChild(refreshBtn);
    toolbar.appendChild(statusEl);
  }

  function load(manual){
    if(manual && state.data && state.data.rows){
      var now = Date.now();
      if(now - state.last < COOLDOWN_MS){
        setCooldown(Math.ceil((COOLDOWN_MS - (now - state.last))/1000));
        return;
      }
    }
    statusEl.textContent = "加载中…";
    setStatus("加载中…", true);
    fetch(BASE+"/top?size="+state.size, {cache:"no-store"})
      .then(function(r){ if(!r.ok) throw new Error("HTTP "+r.status); return r.json(); })
      .then(function(d){
        state.last = Date.now();
        state.data = d;
        setMeta(d);
        renderView();
        setStatus("更新于 " + new Date().toLocaleString("zh-CN",{hour12:false}));
        if(refreshBtn.disabled) enableBtnAfterCooldown();
      })
      .catch(function(e){
        setStatus("加载失败："+esc(e.message));
      });
  }

  function setCooldown(sec){
    if(!refreshBtn) return;
    setStatus("刷新冷却中（"+sec+"s）");
    if(refreshBtn.disabled) return;
    disableBtn();
  }
  function disableBtn(){
    refreshBtn.disabled = true;
    var left = Math.ceil(COOLDOWN_MS/1000);
    refreshBtn.textContent = "刷新 ("+left+"s)";
    var id = setInterval(function(){
      left--;
      if(left<=0){ clearInterval(id); refreshBtn.textContent = "刷新"; refreshBtn.disabled = false; }
      else refreshBtn.textContent = "刷新 ("+left+"s)";
    }, 1000);
  }
  function enableBtnAfterCooldown(){
    if(refreshBtn && refreshBtn.disabled){
      setTimeout(function(){ refreshBtn.disabled = false; refreshBtn.textContent = "刷新"; }, COOLDOWN_MS);
    }
  }

  function init(){
    app = document.getElementById("deepsweApp");
    if(!app) return;
    app.innerHTML = "";
    toolbar = document.createElement("div");
    toolbar.className = "ds-toolbar";
    app.appendChild(toolbar);
    contentEl = document.createElement("div");
    app.appendChild(contentEl);
    buildToolbar();

    if(hasStaticData){
      setMeta(state.data);
      renderView();
      setStatus("更新于 " + new Date().toLocaleString("zh-CN",{hour12:false}));
      // 打开即后台静默刷新一次（CD 之外）
      setTimeout(function(){ load(false); }, 800);
    } else {
      retryUntilData();
    }
  }

  var retries = 0;
  function retryUntilData(){
    load(false);
    if(retries++ < 4){
      setTimeout(retryUntilData, 8000);
    }
  }

  if(document.readyState === "loading"){
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();