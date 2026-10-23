# Launcher Leve - compilar o APK no GitHub (sem instalar nada)

1. Crie uma conta gratuita em github.com e um repositorio novo (New repository), publico ou privado.
2. Envie os arquivos deste projeto para o repositorio:
   - Add file > Upload files, arraste: a pasta `app`, `settings.gradle`, `build.gradle`,
     `gradle.properties` e `.gitignore`. Clique em Commit changes.
3. Crie o arquivo de compilacao (a pasta oculta `.github` costuma nao subir pelo upload):
   - Add file > Create new file
   - No nome, digite exatamente: `.github/workflows/build.yml`
   - Cole o conteudo do arquivo `build.yml` deste pacote. Commit changes.
4. Abra a aba **Actions**. O job "Compilar APK" roda sozinho (2 a 5 minutos).
   Se nao iniciar: Actions > Compilar APK > Run workflow.
5. Quando ficar verde, abra a execucao e, em **Artifacts**, baixe `launcher-leve-apk`
   (vem num zip; dentro esta o `app-debug.apk`).
6. Envie o APK para a box (LocalSend) e instale. Aperte Home e escolha "Launcher Leve".

Se ficar vermelho, abra o job, copie as ultimas linhas do erro e me envie.
