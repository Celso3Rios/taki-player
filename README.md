# Taki Player 📺

Aplicativo **Android nativo em Java**, com reprodução por **Media3 / ExoPlayer** e importação de playlists IPTV **M3U** pelo seletor de arquivos do Android. Projeto pronto para gerar **APK** usando o GitHub Actions.

## O que a versão 1.0 faz

- Importa `.m3u` do armazenamento interno, pendrive/USB visível pelo Android ou gerenciador de arquivos.
- Compatível com listas M3U contendo `#EXTINF`, `tvg-name`, `tvg-logo`, `group-title` e cabeçalhos `#EXT-X-SESSION-DATA`.
- Armazena a biblioteca em **SQLite local**, sem carregar centenas de milhares de itens simultaneamente na memória.
- Paginação de 100 registros, busca por nome, filtros Ao vivo / Filmes / Séries e Novelas / Outros / Tudo, categorias e favoritos.
- Reproduz HLS `.m3u8` e formatos suportados pelo Media3, incluindo muitos `.mp4` e `.mkv` (dependendo do codec).
- Botão **VLC** para formatos/servidores que o ExoPlayer não conseguir reproduzir; toque longo em um item permite copiar a URL.
- Layout escuro e modo tela cheia horizontal.
- Reproduz URLs HTTP para compatibilidade com a playlist do usuário.

### Sua playlist não é publicada

**Importante:** não suba seu arquivo M3U para o GitHub. Algumas listas contêm URLs com usuário/senha ou tokens. A playlist é selecionada **após a instalação** e guardada no banco privado do aplicativo. Os padrões `*.m3u`, `*.m3u8` estão no `.gitignore`, mas confira sempre os arquivos antes de publicar.

## Como gerar o APK pelo GitHub (sem instalar Android Studio)

1. Entre em https://github.com/new e crie um repositório, por exemplo **taki-player**. Pode escolher **Private**. Não adicione README ou .gitignore pelo formulário, pois este projeto já contém ambos.
2. Extraia o ZIP deste projeto no computador.
3. Abra o repositório criado e clique em **Add file → Upload files**. Envie **todo o conteúdo da pasta `TakiPlayer_Android`**, incluindo a pasta oculta `.github` (para o workflow funcionar). Não envie a pasta externa que contém o projeto — os arquivos `app`, `build.gradle` e `.github` devem estar na raiz do repositório.
4. Clique em **Commit changes**.
5. Abra a aba **Actions**. Se necessário, habilite os workflows para o repositório. Escolha **Compilar Taki Player APK** e clique em **Run workflow**; o push inicial na `main` também inicia a compilação automaticamente.
6. Quando a execução mostrar o símbolo de sucesso, abra a execução e baixe **Taki-Player-APK** na seção **Artifacts**. Descompacte o artefato e instale `app-debug.apk` no Android.
7. No Android, abra **Taki Player → IMPORTAR M3U** e selecione sua playlist.

**Observação:** o APK `debug` é assinado automaticamente pelo Android SDK, suficiente para instalar manualmente. Para distribuir atualizações sem desinstalar, uma versão de publicação precisa de assinatura persistente (keystore sob seu controle). Não coloque o keystore ou senhas no repositório.

## Compatibilidade e limitações

- Android **7.0 (API 24)** ou superior; telefone ou TV Box com suporte à instalação de APK e controle por toque/mouse. Controle remoto de Android TV ainda não foi otimizado.
- O Media3 pode reproduzir HLS, MP4 e alguns MKV, mas **AVI/codec incompatível** pode precisar de VLC externo.
- HTTP foi liberado no aplicativo por causa das URLs da lista examinada; em redes públicas isso não oferece criptografia. Prefira fontes HTTPS confiáveis quando possível.
- Um APK supera restrições CORS do navegador, mas **não recupera links expirados**, falhas do servidor, bloqueio por IP, credenciais incorretas ou formatos protegidos (DRM).
- Quando a lista é importada novamente, a biblioteca é substituída em uma transação SQLite, e os favoritos de URLs iguais são preservados.
- O arquivo M3U em si **não foi incluído** neste repositório, e o app não fornece canais nem conteúdo.

## Estrutura do código

```text
.github/workflows/android-apk.yml   GitHub Actions que compila o APK
app/build.gradle                    Dependências Media3, RecyclerView e Glide
app/src/main/java/.../MainActivity.java    Interface, player e controles
app/src/main/java/.../M3uParser.java      Leitor M3U de fluxo
app/src/main/java/.../PlaylistDatabase.java  Banco SQLite e filtros
app/src/main/java/.../EntryAdapter.java    Lista paginada
app/src/test/java/.../M3uParserTest.java   Testes do leitor
```

Versões no projeto: Android Gradle Plugin 8.11.1, Gradle 8.13, JDK 17, Android SDK 36 e Media3 1.11.1.
