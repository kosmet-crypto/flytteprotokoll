/* Bygg app.css på nytt etter endringer i klassene i index.html:
   npx tailwindcss@3 -i src.css -o app.css --minify */
module.exports = { content: ['./index.html'], theme: { extend: {} }, plugins: [] };
