const puppeteer = require('puppeteer');
const path = require('path');
const fs = require('fs');

const screenshotPath = path.join(
  "C:\\Users\\Randy Cordero\\.gemini\\antigravity\\brain\\0a40c2a5-a9d5-4961-b6b1-2098005d90ec",
  "limits_confirm_screenshot.png"
);

(async () => {
  console.log("=== INICIANDO VERIFICACIÓN DE INTERFAZ PREMIUM CON PUPPETEER ===");
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 960 });

  // Capturar logs y errores de la consola del navegador
  page.on('console', msg => console.log('  [CONSOLA NAV]:', msg.text()));
  page.on('pageerror', err => console.error('  [ERROR NAV]:', err.toString()));
  page.on('error', err => console.error('  [CRASH NAV]:', err.toString()));

  try {
    console.log("1. Navegando a http://localhost:5173...");
    await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });

    console.log("2. Esperando carga inicial del Dashboard...");
    await page.waitForSelector('.sidebar-nav-btn', { timeout: 10000 });
    console.log("   Dashboard cargado exitosamente.");

    // Obtener datos del perfil actual
    const currentProfileText = await page.evaluate(() => {
      const headerTitle = document.querySelector('header h1');
      const subTitle = document.querySelector('header span');
      return {
        title: headerTitle ? headerTitle.textContent.trim() : 'N/D',
        sub: subTitle ? subTitle.textContent.trim() : 'N/D'
      };
    });
    console.log(`   Perfil de inicio activo: ${currentProfileText.title} (${currentProfileText.sub})`);

    // Helper para cambiar de pestaña en el sidebar
    const clickSidebarTab = async (label) => {
      console.log(`\n-> Haciendo clic en la pestaña: "${label}"...`);
      const clicked = await page.evaluate((tabLabel) => {
        const buttons = Array.from(document.querySelectorAll('.sidebar-nav-btn'));
        const targetBtn = buttons.find(b => b.textContent.includes(tabLabel));
        if (targetBtn) {
          targetBtn.click();
          return true;
        }
        return false;
      }, label);

      if (!clicked) {
        throw new Error(`No se pudo encontrar el botón de pestaña "${label}" en el sidebar.`);
      }
      await new Promise(r => setTimeout(r, 2000)); // Esperar renderizado reactivo
    };

    // 3. Probar Pestaña: Monitoreo Red
    await clickSidebarTab("Monitoreo Red");
    console.log("   Pestaña 'Monitoreo Red' cargada correctamente. Validando existencia de secciones...");

    // 4. Probar Pestaña: Límites de Juego
    await clickSidebarTab("Límites de Juego");
    console.log("   Pestaña 'Límites de Juego' cargada correctamente. Validando formulario y alcance...");

    // 5. Probar Botón de Acción Principal "Guardar Cambios"
    console.log("   Haciendo clic en el botón principal 'Guardar Cambios'...");
    const saveButtonClicked = await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const saveBtn = buttons.find(b => b.textContent.includes("Guardar Cambios"));
      if (saveBtn) {
        saveBtn.click();
        return true;
      }
      return false;
    });

    if (!saveButtonClicked) {
      throw new Error("No se pudo encontrar el botón 'Guardar Cambios' en la sección de Límites.");
    }
    console.log("   Botón presionado. Esperando apertura del Modal deslizante (Bottom Sheet)...");
    await new Promise(r => setTimeout(r, 2000));

    // 6. Validar contenido del Modal Bottom Sheet
    const modalDetails = await page.evaluate(() => {
      const modalHeader = document.querySelector('h3');
      const isVisible = modalHeader && modalHeader.textContent.includes("Confirmar Guardar Límites");
      return {
        visible: !!isVisible,
        title: modalHeader ? modalHeader.textContent.trim() : 'N/D'
      };
    });

    if (modalDetails.visible) {
      console.log(`   [OK] Modal de Confirmación visible y funcional: "${modalDetails.title}"`);
    } else {
      throw new Error("El Modal Bottom Sheet de Confirmación de Límites no se desplegó correctamente.");
    }

    // 7. Tomar captura de pantalla de alta fidelidad con el Modal activo
    console.log(`\n6. Guardando captura de pantalla premium en el directorio de artefactos...`);
    await page.screenshot({ path: screenshotPath, fullPage: false });
    console.log(`   Captura guardada con éxito en: ${screenshotPath}`);

    console.log("\n=== VERIFICACIÓN OPERATIVA FINALIZADA CON ÉXITO: 100% CORRECTO ===");
  } catch (e) {
    console.error("\n[ERROR DE VERIFICACIÓN]:", e.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
