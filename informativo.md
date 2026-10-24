# Launcher para TV Box MXQ Pro 4K

Launcher personalizado para a TV Box MXQ Pro 4K, criado para deixar o aparelho mais leve, rápido e prático de usar.

O design da tela inicial foi feito no [Inkscape](https://github.com/melquesedque/launcher-tvbox-mxq/blob/1df1725797d35262dddc64df7967efe014a96c27/launcher.png), e o código-fonte e os arquivos necessários para gerar o APK pelo GitHub foram desenvolvidos com o auxílio do Claude, a IA da Anthropic.

## Funcionalidades

* **Relógio e data** sempre visíveis na tela inicial
* **Temperatura e previsão do dia** para a sua região
* **Cinco atalhos para aplicativos favoritos**, posicionados no centro da tela
* **Informações da mídia em reprodução**, mostrando o que está tocando no momento
* **Botão de todos os aplicativos**, com acesso à lista completa
* **Botão de configurações** do Android TV

## Personalização

Antes de gerar o APK, ajuste os itens abaixo no arquivo `app/src/main/java/com/leve/launcher/MainActivity.java`.

### Temperatura e previsão do dia

Altere a latitude e a longitude para as coordenadas da sua localização.

### Botão de configurações

O pacote de configurações varia de acordo com o aparelho. Para usar o launcher em outro dispositivo, altere a linha abaixo, substituindo `NOME DO APP` pelo nome do pacote do aplicativo de configurações do seu aparelho:

```java
private static final String PACOTE_CONFIG = "NOME DO APP";
```
