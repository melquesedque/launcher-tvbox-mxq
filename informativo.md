# Launcher para TV BOX MXQ Pro 4K

Projeto criado para melhorar o desempenho do aparelho da melhor forma possível. Utilizando a imagem criada no Inkscape(https://github.com/melquesedque/launcher-tvbox-mxq/blob/1df1725797d35262dddc64df7967efe014a96c27/launcher.png) o Claude IA desenvolveu os arquivos essenciais para a gereção do APK no Github.

## Funcionalidades:
* Relógio e Data
* Temperatura e previsão do dia
* 5 espaços para aplicativos favoritos no centro da tela
* Informativo do que esta tocando no momento
* Botão que leva para todos os aplicativos
* Botão de configuração android TV

## Observação:
* Temperatura e previsão do dia: Modificar a latitude e longitude no arquivo "app/src/main/java/com/leve/launcher/MainActivity.java"
* Botão de configuração: Para utilizar em outro aparelho é necessário modificar o aplicativo no arquivo "app/src/main/java/com/leve/launcher/MainActivity.java" e alterar a linha "private static final String PACOTE_CONFIG = "NOME DO APP";"
