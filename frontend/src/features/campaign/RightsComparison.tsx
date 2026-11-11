import {useMemo,useState} from 'react';
import {CellStyleModule,ClientSideRowModelModule,ColumnApiModule,PinnedRowModule,themeQuartz,ValidationModule} from 'ag-grid-community';
import type {ColDef,ColumnState,ICellRendererParams} from 'ag-grid-community';
import {AgGridReact} from 'ag-grid-react';
import {money} from '../../api';
import type {Assessment,RightsRow,Usage} from '../../api';

const modules=[ClientSideRowModelModule,ColumnApiModule,PinnedRowModule,CellStyleModule,...(import.meta.env.DEV?[ValidationModule]:[])];
const theme=themeQuartz.withParams({accentColor:'#5152d6',backgroundColor:'#ffffff',foregroundColor:'#172033',borderColor:'#dde2eb',headerBackgroundColor:'#f7f8fb',fontFamily:'inherit',fontSize:12,headerFontSize:11,rowBorder:true,wrapperBorder:false,wrapperBorderRadius:0,cellHorizontalPadding:12});
type Row=RightsRow & {summary?:boolean;summaryText?:string};
const title=(value:string)=>value.replaceAll('_',' ').toLowerCase();
function status(row:RightsRow){return row.status==='COVERED'?'Already covered':row.status==='UPGRADE_REQUIRED'?'Upgrade needed':title(row.status)}
function scopeLines(scope:Usage|null){return scope?[scope.distributions.map(title).join(' + '),scope.territories.map(t=>({US:'United States',GB:'United Kingdom',WORLDWIDE:'Worldwide'}[t]??t)).join(' + '),scope.platforms.map(title).join(' + '),`${new Date(scope.startDate+'T00:00:00Z').toLocaleDateString('en-GB',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'})} → ${new Date(scope.endDateExclusive+'T00:00:00Z').toLocaleDateString('en-GB',{day:'numeric',month:'short',year:'numeric',timeZone:'UTC'})} (end exclusive)`]:['No active grant','','','']}
function ScopeChanges({row,changedOnly}:{row:RightsRow;changedOnly:boolean}){
  const held=scopeLines(row.heldScope),requested=scopeLines(row.requestedScope);
  const labels=['Distribution','Territories','Platforms','Dates'];
  const reasons=['DISTRIBUTION_GAP','TERRITORY_GAP','PLATFORM_GAP','DATE_GAP'];
  const indices=labels.map((_,i)=>i).filter(i=>!changedOnly||row.reasonCodes.includes(reasons[i]));
  return <div className="scope-changes">{indices.length===0?<span className="fine">Requested use fits the held permission. Broader existing rights remain yours.</span>:indices.map(i=><div key={labels[i]}><span>{labels[i]}</span><p><span className="scope-before">Held permission: {held[i]||'—'}</span><span> · Requested use: </span><strong>{requested[i]}</strong></p></div>)}</div>;
}
function AssetCell({data}:ICellRendererParams<Row>){if(!data)return null;return data.summary?<div className="grid-summary"><strong>All {data.summaryText}</strong><span>Full campaign · server total</span></div>:<div className="grid-asset"><img src={data.previewPath} alt=""/><div><strong>{data.assetName}</strong><span className={`coverage ${data.status==='COVERED'?'covered':'upgrade'}`}>{status(data)}</span><span className="grid-tier">{data.heldTier??'No grant'}{data.targetTier?` → ${data.targetTier}`:''}</span></div></div>}

export function RightsComparison({assessment,onEvidence}:{assessment:Assessment;onEvidence:(row:RightsRow)=>void}){
  const [filter,setFilter]=useState('all'),[changedOnly,setChangedOnly]=useState(true);
  const rows=useMemo(()=>assessment.rows.filter(row=>filter==='all'||row.status===filter),[assessment,filter]);
  const upgrades=assessment.rows.filter(row=>row.status==='UPGRADE_REQUIRED').length,covered=assessment.rows.filter(row=>row.status==='COVERED').length;
  const columns=useMemo<ColDef<Row>[]>(()=>[
    {field:'assetName',headerName:'Asset & held rights',minWidth:190,flex:1,cellRenderer:AssetCell},
    {colId:'scope',headerName:changedOnly?'Missing rights from server assessment':'Held permission / requested use',minWidth:255,flex:1.4,sortable:false,cellRenderer:({data}:ICellRendererParams<Row>)=>data&&!data.summary?<ScopeChanges row={data} changedOnly={changedOnly}/>:<span className="grid-summary-label">{upgrades} upgrades · {covered} already covered</span>},
    {field:'deltaMinor',headerName:'Additional',width:112,minWidth:100,type:'numericColumn',valueFormatter:p=>money(p.value??0),cellClass:'grid-money'},
    {colId:'evidence',headerName:'Source',width:126,minWidth:116,sortable:false,cellRenderer:({data}:ICellRendererParams<Row>)=>data&&!data.summary?<button className="text-button grid-evidence" aria-label={`Policy evidence for ${data.assetName}`} onClick={()=>onEvidence(data)}>Policy evidence ↗</button>:null}
  ],[changedOnly,upgrades,covered,onEvidence]);
  const pinned=useMemo<Row[]>(()=>[{...assessment.rows[0],assetId:'campaign-summary',assetName:'All assets',deltaMinor:assessment.totalDeltaMinor,summary:true,summaryText:`${assessment.rows.length} assets`}],[assessment]);
  return <>
    <div className="comparison-toolbar"><label>Show assets<select aria-label="Show assets" value={filter} onChange={e=>setFilter(e.target.value)}><option value="all">All assets ({assessment.rows.length})</option><option value="UPGRADE_REQUIRED">Needs upgrade ({upgrades})</option><option value="COVERED">Already covered ({covered})</option></select></label><label className="checkbox"><input type="checkbox" checked={changedOnly} onChange={e=>setChangedOnly(e.target.checked)}/>Only missing rights</label></div>
    <div className="comparison-grid" aria-label="Rights comparison"><AgGridReact<Row> modules={modules} theme={theme} columnDefs={columns} rowData={rows} pinnedBottomRowData={pinned} defaultColDef={{resizable:true,sortable:true}} domLayout="autoHeight" rowHeight={194} headerHeight={46} getRowHeight={p=>p.node.rowPinned?72:194} getRowId={p=>p.data.assetId} ensureDomOrder animateRows={false} suppressCellFocus={false} tooltipShowDelay={0}
      onGridReady={event=>{try{const saved=JSON.parse(sessionStorage.getItem('cl-rights-columns:v1')??'null') as ColumnState[]|null;if(Array.isArray(saved))event.api.applyColumnState({state:saved})}catch{/* A malformed preference must not prevent the comparison. */}}}
      onColumnResized={event=>{if(event.finished)sessionStorage.setItem('cl-rights-columns:v1',JSON.stringify(event.api.getColumnState().map(({colId,width,flex})=>({colId,width,flex}))))}}
    /></div>
    <div className="comparison-cards">{rows.map(row=><article key={row.assetId} className="rights-row"><div><h3>{row.assetName}</h3><p className="fine">Held: {row.heldTier??'No active grant'}{row.targetTier?` → ${row.targetTier}`:''}</p></div><span className={row.status==='COVERED'?'coverage covered':'coverage upgrade'}>{status(row)}</span><strong>{money(row.deltaMinor)}</strong><div className="mobile-scope"><ScopeChanges row={row} changedOnly={changedOnly}/></div><button className="text-button" onClick={()=>onEvidence(row)}>Policy evidence ↗</button></article>)}{rows.length===0&&<p className="fine" role="status">No assets match this filter.</p>}<div className="mobile-total"><span>Full campaign total</span><strong>{money(assessment.totalDeltaMinor)}</strong></div></div>
    <p className="fine comparison-help">Sort by asset or additional cost. Existing permissions are retained. Dates show an exclusive end. Filters change the view; the quote always includes the complete confirmed campaign.</p>
  </>;
}
