import {test,expect} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
const state=path.resolve(import.meta.dirname,'../../../.local/browser-test-state.json');
test('real Gemini evidence confirmation, refresh recovery and unsafe-use blocking',async({page})=>{
  if(fs.existsSync(state))await page.context().addCookies(JSON.parse(fs.readFileSync(state,'utf8')).cookies);
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/');await page.getByRole('button',{name:/^(Open sample campaign|Resume campaign) →$/}).click();
  await page.getByRole('button',{name:'Use example brief',exact:true}).click();
  await page.getByRole('button',{name:'Interpret revision →',exact:true}).click();
  page.once('dialog',dialog=>dialog.accept());await page.reload();
  await expect(page.getByRole('heading',{name:'Review the proposed usage',exact:true})).toBeVisible({timeout:100000});
  await expect(page.getByLabel('Proposed territories',{exact:true})).toHaveValue(/GB.*US|US.*GB/);
  await expect(page.getByLabel('Proposed end date (exclusive)',{exact:true})).toHaveValue('2027-02-13');
  await expect(page.getByText('Inherited from confirmed usage',{exact:true}).first()).toBeVisible();
  const proposed=page.getByRole('button',{name:'Confirm usage & compare rights →',exact:true});await expect(proposed).toBeDisabled();
  for(const [width,height]of [[1440,900],[1024,768],[390,844]]){await page.setViewportSize({width,height});await expect(page.locator(width<768?'.comparison-cards':'.comparison-grid')).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:path.resolve(import.meta.dirname,`../../../docs/evidence/ai-proposal-${width}x${height}.png`),fullPage:true})}
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('checkbox',{name:/I reviewed the original brief/}).check();await proposed.click();
  await expect(page.getByText(/AI PROPOSAL · USER CONFIRMED/)).toBeVisible();
  await expect(page.locator('.quote-total')).toHaveText('$20.00');
  for(const [width,height]of [[1440,900],[1024,768],[390,844]]){await page.setViewportSize({width,height});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:path.resolve(import.meta.dirname,`../../../docs/evidence/ai-confirmed-${width}x${height}.png`),fullPage:true})}
  await page.setViewportSize({width:1440,height:900});
  for(const brief of ['Run paid ads for 90 days. The campaign must end after 30 days.','Also use this in television commercials.']){
    await page.getByLabel('Client brief',{exact:true}).fill(brief);await page.getByRole('button',{name:'Interpret revision →',exact:true}).click();
    await expect(page.getByRole('region',{name:'Review proposed usage'})).toBeVisible({timeout:100000});
    await expect(page.getByRole('button',{name:'Confirm usage & compare rights →',exact:true})).toBeDisabled();
    await expect(page.getByRole('button',{name:/^(Review upgrade quote →|Accept quote & continue →|Refresh quote)$/})).toBeDisabled();
    if(brief.includes('television'))await expect(page.getByText('Checkout blocked for this brief',{exact:true})).toBeVisible();
    else await expect(page.getByText(/CONFLICT/).first()).toBeVisible();
  }
  await page.screenshot({path:path.resolve(import.meta.dirname,'../../../docs/evidence/ai-unsupported-desktop.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:path.resolve(import.meta.dirname,'../../../docs/evidence/ai-unsupported-390x844.png'),fullPage:true});
  expect(errors).toEqual([]);
});
