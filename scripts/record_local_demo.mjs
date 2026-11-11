// Records only the developed localhost app. No PayPal order, approval or capture is requested.
import {chromium} from '../frontend/node_modules/playwright/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
process.umask(0o077);
const root=path.resolve(import.meta.dirname,'..'),folder=path.join(root,'.local/video');fs.mkdirSync(folder,{recursive:true,mode:0o700});
const browser=await chromium.launch({headless:true,executablePath:process.env.CUTLEDGER_BROWSER_PATH??'/usr/bin/brave-browser'});
const state=path.join(folder,'browser-state.json');
const context=await browser.newContext({...(fs.existsSync(state)?{storageState:state}:{}),viewport:{width:1440,height:900},recordVideo:{dir:path.join(folder,'raw'),size:{width:1440,height:900}}});
const page=await context.newPage(),start=Date.now(),scenes=[];
async function caption(text,seconds=0){scenes.push({seconds:Math.round((Date.now()-start)/1000),caption:text});await page.evaluate(text=>{let el=document.querySelector('#recording-caption');if(!el){el=document.createElement('div');el.id='recording-caption';Object.assign(el.style,{position:'fixed',bottom:'18px',left:'50%',transform:'translateX(-50%)',zIndex:'99999',maxWidth:'90vw',padding:'12px 22px',borderRadius:'10px',background:'#172033ee',color:'white',font:'600 20px system-ui',textAlign:'center',pointerEvents:'none'});document.body.appendChild(el)}el.textContent=text},text);if(seconds)await page.waitForTimeout(seconds*1000)}
let status='INCOMPLETE';
try{
 await page.goto('http://localhost:8080/');await page.getByRole('button',{name:/^(Open sample campaign|Resume campaign) →$/}).waitFor();
 await caption('CutLedger · New brief. Same assets. Just the difference.',5);
 await page.getByRole('button',{name:/^(Open sample campaign|Resume campaign) →$/}).click();await page.getByRole('heading',{name:'Northstar Launch',exact:true}).waitFor();await caption('Three original assets. Existing permissions stay in the ledger.',6);
 await page.getByRole('button',{name:'Use example brief',exact:true}).click();await page.getByLabel('Client brief',{exact:true}).scrollIntoViewIfNeeded();await caption('The client now wants paid ads in the US and UK for 90 days.',4);
 await page.getByRole('button',{name:/^(Interpret revision →|Retry interpretation)$/}).click();await caption('Real hosted Gemini proposes edits. It never decides prices or coverage.');
 await Promise.race([page.getByRole('heading',{name:'Review the proposed usage',exact:true}).waitFor({timeout:90000}),page.getByText(/Could not reliably interpret this revision/).waitFor({timeout:90000}).then(()=>{throw new Error('Provider unavailable after bounded retry; draft incomplete')})]);await page.getByRole('region',{name:'Review proposed usage'}).scrollIntoViewIfNeeded();await caption('Literal evidence, inherited fields and an exact date preview.',6);
 await page.getByRole('checkbox',{name:/I reviewed the original brief/}).check();await page.getByRole('button',{name:'Confirm usage & compare rights →',exact:true}).click();
 await page.getByText(/AI PROPOSAL · USER CONFIRMED/).waitFor();await page.locator('.comparison-grid').waitFor();await page.locator('.comparison').scrollIntoViewIfNeeded();await caption('You confirm the usage. The deterministic backend calculates the difference.',6);
 if((await page.locator('.quote-total').innerText())!=='$20.00')throw new Error('Unexpected server assessment');
 await page.getByLabel('Show assets',{exact:true}).selectOption('UPGRADE_REQUIRED');await caption('Two upgrades: $8 + $12. Paper Orbit is already covered.',5);await page.getByLabel('Show assets',{exact:true}).selectOption('all');
 await page.getByRole('button',{name:'Policy evidence for Harbor Lines',exact:true}).click();await page.getByRole('dialog').waitFor();await caption('Every gap points back to the merchant’s policy and prior credit.',5);await page.getByRole('button',{name:'Close policy evidence',exact:true}).click();
 await page.getByRole('button',{name:'Review upgrade quote →',exact:true}).click();await page.getByRole('button',{name:'Accept quote & continue →',exact:true}).waitFor();await page.locator('aside').scrollIntoViewIfNeeded();await caption('An immutable ten-minute server quote. Sandbox approval and capture are explicit.',5);
 await page.getByLabel('Client brief',{exact:true}).fill('Also use this in television commercials.');await page.getByLabel('Client brief',{exact:true}).scrollIntoViewIfNeeded();await page.getByRole('button',{name:/^(Interpret revision →|Retry interpretation)$/}).click();await caption('Unsupported use is kept visible. No invented price or clearance.');
 await Promise.race([page.getByText('Checkout blocked for this brief',{exact:true}).waitFor({timeout:90000}),page.getByText(/Could not reliably interpret this revision/).waitFor({timeout:90000}).then(()=>{throw new Error('Provider unavailable during unsupported check; draft incomplete')})]);await page.getByRole('region',{name:'Review proposed usage'}).scrollIntoViewIfNeeded();await caption('Television stays unsupported. Checkout is blocked until the brief is resolved.',7);
 await caption('Local demo draft · No payment is completed in this recording.',6);
 status='FIRST_PARTY_DRAFT_COMPLETE_NO_PAYMENT';
 await context.storageState({path:path.join(folder,'browser-state.json')});
}catch(error){scenes.push({seconds:Math.round((Date.now()-start)/1000),error:error.message});status='INCOMPLETE_PROVIDER_OR_UI_FAILURE';}
await context.storageState({path:state});
const video=page.video();await context.close();await video.saveAs(path.join(folder,'CutLedger-local-draft.webm'));await browser.close();
fs.writeFileSync(path.join(folder,'recording.json'),JSON.stringify({status,recordedAt:new Date().toISOString(),durationSeconds:Math.round((Date.now()-start)/1000),scenes,scope:'Actual localhost app and real hosted AI. Video captions are recording annotations. No payment order/approval/capture or private financial evidence is recorded.'},null,2)+'\n');
console.log(JSON.stringify({status,durationSeconds:Math.round((Date.now()-start)/1000),path:path.join(folder,'CutLedger-local-draft.webm')}));if(status!=='FIRST_PARTY_DRAFT_COMPLETE_NO_PAYMENT')process.exitCode=1;
