import {test,expect} from '@playwright/test';
import type {Page} from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
const privateState=path.resolve(import.meta.dirname,'../../../.local/browser-test-state.json');
async function openCampaign(page:Page){
  // Reuse this test browser's workspace across repeated local runs; never reset
  // session quotas or financial history merely to get a green test.
  if(fs.existsSync(privateState))await page.context().addCookies(JSON.parse(fs.readFileSync(privateState,'utf8')).cookies);
  await page.goto('/');
  await page.getByRole('button',{name:/^(Open sample campaign|Resume campaign) →$/}).click();
  await expect(page.getByRole('heading',{name:'Northstar Launch',exact:true})).toBeVisible();
}

test('manual revision, exact server quote, evidence and responsive refresh recovery',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  await openCampaign(page);
  const campaign=page.url();
  const diagnostics=await (await page.request.get('/api/v1/diagnostics')).json();
  await page.reload();
  await expect(page.getByRole('heading',{name:'Northstar Launch',exact:true})).toBeVisible();
  expect(page.url()).toBe(campaign);
  await page.getByText('Enter usage manually',{exact:true}).click();
  await page.getByRole('button',{name:'Example: paid US + UK, 90 days',exact:true}).click();
  for(const control of await page.locator('.manual-usage').getByRole('checkbox',{name:/^(Exclude from this permission:|Resolve )/}).all())await control.check();
  await page.getByRole('checkbox',{name:/I confirm this exact usage/}).check();
  await page.getByRole('button',{name:'Confirm manual revision',exact:true}).click();
  await expect(page.locator('.comparison>p.muted')).toHaveText('2 upgrades · 1 already covered');
  await expect(page.locator('.rights-row').filter({hasText:'Harbor Lines'})).toContainText('$8.00');
  await expect(page.locator('.rights-row').filter({hasText:'Kinetic Shapes'})).toContainText('$12.00');
  await expect(page.locator('.rights-row').filter({hasText:'Paper Orbit'})).toContainText('$0.00');
  await page.getByRole('button',{name:'Review upgrade quote →',exact:true}).click();
  await expect(page.locator('.quote-total')).toHaveText('$20.00');
  await expect(page.getByText('Manual entry is a separate, labeled fallback. AI never decides coverage, money or payment status.')).toBeVisible();
  const grid=page.locator('.comparison-grid');
  await expect(grid).toBeVisible();
  await expect(grid.locator('[row-index="0"]')).toContainText('Harbor Lines');
  await page.getByLabel('Show assets',{exact:true}).selectOption('UPGRADE_REQUIRED');
  await expect(grid.getByText('Paper Orbit',{exact:true})).toHaveCount(0);
  await expect(grid.locator('.ag-row-pinned')).toContainText('$20.00');
  await page.getByLabel('Show assets',{exact:true}).selectOption('all');
  await grid.getByRole('columnheader',{name:'Additional',exact:true}).click();
  await expect(grid.locator('[row-index="0"]')).toContainText('Paper Orbit');
  await grid.getByRole('columnheader',{name:'Additional',exact:true}).click();
  await expect(grid.locator('[row-index="0"]')).toContainText('Kinetic Shapes');
  await page.getByRole('checkbox',{name:'Only missing rights',exact:true}).uncheck();
  await expect(grid.getByText('Held permission / requested use',{exact:true})).toBeVisible();
  await page.getByRole('checkbox',{name:'Only missing rights',exact:true}).check();
  await page.getByRole('button',{name:'Policy evidence for Harbor Lines',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('V1-UPGRADE-CREDIT');
  await page.getByRole('button',{name:'Close policy evidence',exact:true}).click();
  fs.mkdirSync(path.dirname(privateState),{recursive:true});
  await page.context().storageState({path:privateState});
  fs.chmodSync(privateState,0o600);
  for(const [width,height] of [[1440,900],[1024,768],[390,844]]){
    await page.setViewportSize({width,height});
    await expect(page.locator('.asset')).toHaveCount(3);
    if(diagnostics.aiAvailable)await expect(page.getByLabel('Client brief',{exact:true})).toBeEnabled();else await expect(page.getByLabel('Client brief',{exact:true})).toBeDisabled();
    await expect(page.locator('.quote-total')).toHaveText('$20.00');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    if(width===390){
      await expect(page.locator('.comparison-cards')).toBeVisible();
      await page.locator('.comparison-cards').getByRole('button',{name:'Policy evidence ↗'}).first().click();
      await expect(page.getByRole('dialog')).toContainText('V1-UPGRADE-CREDIT');
      await page.getByRole('button',{name:'Close policy evidence',exact:true}).click();
    }
    await page.screenshot({path:path.resolve(import.meta.dirname,'../../../docs/evidence',`comparison-${width}x${height}.png`),fullPage:true});
  }
  await page.getByRole('button',{name:'Permissions',exact:true}).click();
  await page.reload();
  await expect(page.getByRole('heading',{name:'Rights already held'})).toBeVisible();
  await expect(page.getByText('Seeded prior permission · no historical payment claimed')).toHaveCount(3);
  await page.getByRole('button',{name:'Activity',exact:true}).click();
  await expect(page.getByRole('heading',{name:'A clear record of each change'})).toBeVisible();
  await page.getByRole('button',{name:'Workspace',exact:true}).click();
  await page.keyboard.press('Tab');
  expect(errors).toEqual([]);
});

test('network failure offers recovery and no purchase action',async({page})=>{
  await page.route('**/api/v1/diagnostics',route=>route.abort());
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Cannot reach CutLedger');
  await expect(page.getByRole('button',{name:'Open sample campaign →',exact:true})).toBeDisabled();
  await expect(page.getByRole('button',{name:'Retry',exact:true})).toBeEnabled();
});

test('missing owned checkout shows a recoverable error instead of indefinite loading',async({page})=>{
  await openCampaign(page);
  await page.goto('/checkout/00000000-0000-4000-8000-000000000000');
  await expect(page.getByRole('alert')).toContainText('unavailable');
  await expect(page.getByRole('button',{name:'Retry existing checkout',exact:true})).toBeEnabled();
  await expect(page.getByText('Loading the existing checkout…',{exact:true})).toHaveCount(0);
  await page.getByRole('link',{name:'← Return to campaign',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Northstar Launch',exact:true})).toBeVisible();
});


test('draft survives cancelled Back navigation and policy dialog returns keyboard focus',async({page})=>{
  await openCampaign(page);const campaign=page.url();const diagnostics=await(await page.request.get('/api/v1/diagnostics')).json();
  if(diagnostics.aiAvailable){
    await page.getByLabel('Client brief',{exact:true}).fill('A draft that has not been interpreted.');
    page.once('dialog',dialog=>dialog.dismiss());await page.goBack();
    await expect(page).toHaveURL(campaign);await expect(page.getByLabel('Client brief',{exact:true})).toHaveValue('A draft that has not been interpreted.');
    page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Permissions',exact:true}).click();await page.getByRole('button',{name:'Workspace',exact:true}).click();
  }
  const evidence=page.getByRole('button',{name:'Policy evidence for Harbor Lines',exact:true});await evidence.focus();await page.keyboard.press('Enter');await expect(page.getByRole('dialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).not.toBeVisible();await expect(evidence).toBeFocused();
});

 test('blocked zero assessment and unconfirmed draft never claim coverage',async({page})=>{
 await openCampaign(page);
 await page.route('**/assessment',async route=>{const response=await route.fetch();const body=await response.json();body.totalDeltaMinor=0;body.canQuote=false;body.rows=body.rows.map((r:object)=>({...r,status:'REVIEW_REQUIRED',deltaMinor:0,reasonCodes:['PAYMENT_REVIEW']}));await route.fulfill({json:body});});
 await page.reload();await expect(page.getByRole('heading',{name:'Permission review required.'})).toBeVisible();await expect(page.getByRole('heading',{name:'Rights already held.'})).toHaveCount(0);
 await page.getByLabel('Client brief',{exact:true}).fill('Also television');await expect(page.getByRole('heading',{name:'Draft needs review.'})).toBeVisible();await expect(page.getByText('This assessment does not cover your unconfirmed draft.',{exact:false})).toBeVisible();
 });
 test('manual fallback retains prior warning during a pending replacement',async({page})=>{
 const fixtureId='00000000-0000-4000-8000-000000000001';let usage:unknown,base:string;
 await page.route('**/api/v1/campaigns/*',async route=>{const response=await route.fetch();const body=await response.json();if(body.latestRevision){usage=body.latestRevision.usage;base=body.latestRevision.id;body.latestInterpretationId=fixtureId;}await route.fulfill({json:body});});
 await page.route('**/api/v1/interpretations/'+fixtureId,async route=>{const warning={id:'00000000-0000-4000-8000-000000000002',status:'UNSUPPORTED',brief:'TEST-ONLY fixture: television',baseRevisionId:base,model:'TEST_ONLY',referenceDate:'2026-11-11',normalizedCandidate:usage,issues:[],evidence:[],result:{provenance:{},unsupportedRequests:[{id:'tv',requirement:'TEST-ONLY television exclusion'}]}};await route.fulfill({json:{...warning,id:fixtureId,status:'PENDING',normalizedCandidate:null,result:null,manualSafeguard:warning}});});
 await openCampaign(page);await page.getByText('Enter usage manually',{exact:true}).click();await expect(page.getByRole('checkbox',{name:'Exclude from this permission: TEST-ONLY television exclusion'})).toBeVisible();await expect(page.getByRole('button',{name:'Confirm manual revision',exact:true})).toBeDisabled();
 await page.setViewportSize({width:390,height:844});await expect(page.getByRole('checkbox',{name:'Exclude from this permission: TEST-ONLY television exclusion'})).toBeVisible();
 });
