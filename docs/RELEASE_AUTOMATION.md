# Release automation

## Portugues Brasil

Toda nova release com APK deve ter verificacao VirusTotal publicada no GitHub.

O workflow `.github/workflows/virustotal-apk.yml` executa automaticamente quando uma release
e publicada. Ele tambem pode ser executado manualmente em **Actions > VirusTotal APK scan**
informando a tag da release.

O workflow faz:

- baixa o APK anexado na release;
- envia o APK para o VirusTotal;
- espera a analise terminar;
- gera `VIRUSTOTAL.md` em Portugues, Ingles e Espanhol;
- anexa `VIRUSTOTAL.md` na release;
- atualiza as notas da release com o resumo e o link do relatorio.

Configuracao obrigatoria:

- adicionar o secret `VIRUSTOTAL_API_KEY` em **GitHub > Settings > Secrets and variables > Actions**.

Nunca salve a chave do VirusTotal em arquivo do repositorio.

---

## English US

Every new release with an APK must publish the VirusTotal result on GitHub.

The workflow `.github/workflows/virustotal-apk.yml` runs automatically when a release is
published. It can also be run manually from **Actions > VirusTotal APK scan** with the
release tag.

The workflow:

- downloads the APK attached to the release;
- uploads the APK to VirusTotal;
- waits for analysis completion;
- generates `VIRUSTOTAL.md` in Portuguese, English, and Spanish;
- attaches `VIRUSTOTAL.md` to the release;
- updates the release notes with the summary and report link.

Required setup:

- add the `VIRUSTOTAL_API_KEY` secret in **GitHub > Settings > Secrets and variables > Actions**.

Never store the VirusTotal key in repository files.

---

## Espanol

Cada nueva release con APK debe publicar el resultado de VirusTotal en GitHub.

El workflow `.github/workflows/virustotal-apk.yml` se ejecuta automaticamente cuando se
publica una release. Tambien se puede ejecutar manualmente desde **Actions > VirusTotal APK
scan** indicando la tag de la release.

El workflow:

- descarga el APK adjunto en la release;
- envia el APK a VirusTotal;
- espera a que termine el analisis;
- genera `VIRUSTOTAL.md` en Portugues, Ingles y Espanol;
- adjunta `VIRUSTOTAL.md` en la release;
- actualiza las notas de la release con el resumen y el link del informe.

Configuracion obligatoria:

- agregar el secret `VIRUSTOTAL_API_KEY` en **GitHub > Settings > Secrets and variables > Actions**.

Nunca guardes la clave de VirusTotal en archivos del repositorio.
