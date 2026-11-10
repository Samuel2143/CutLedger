import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir:'./tests/e2e',workers:1,reporter:'list',
  use:{baseURL:'http://localhost:8080',headless:true,launchOptions:{executablePath:process.env.CUTLEDGER_BROWSER_PATH}},
});
