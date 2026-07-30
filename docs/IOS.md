# iOS: desenvolvimento, build e suporte

O TUSB para iOS é um app SwiftUI com um núcleo Swift Package (`TUSBCore`) e um
transporte simulado para desenvolvimento. A interface e o protocolo podem ser
compilados e testados no iPhone Simulator. **Não há transporte USB físico
validado para iPhone.**

## Estado atual

| Recurso | Simulator | iPhone físico | iPad físico |
|---|---:|---:|---:|
| Interface SwiftUI e navegação | Sim | Sim | Sim |
| Núcleo, codec e estado do pedal | Sim | Sim | Sim |
| Pedal simulado | Sim | Sim | Sim |
| Testes automatizados | Sim | Não se aplica | Não se aplica |
| ToneX One por USB CDC-ACM genérico | Não | **Não há API pública** | Não há API pública genérica |
| `ExternalAccessory` | Não | Somente acessório/protocolo MFi autorizado | Somente acessório/protocolo MFi autorizado |
| `USBDriverKit` | Não | **Não disponível** | Somente iPad com chip M, entitlement e driver |
| MIDI e microfone físicos | Parcial/simulado | Possível via APIs Apple, não validado neste projeto | Possível via APIs Apple, não validado neste projeto |

> O código e o CI validam software no Simulator. Não houve validação com iPhone,
> iPad, ToneX One, cabo, hub, controlador MIDI ou microfone físicos.

## Limites da plataforma Apple

O ToneX One se apresenta ao Android como dispositivo USB que exige comunicação
CDC-ACM. iOS não oferece uma API pública genérica para um app abrir esse tipo de
interface USB no iPhone.

- [`ExternalAccessory`](https://developer.apple.com/documentation/externalaccessory)
  comunica com acessórios MFi. O fabricante decide quais apps e protocolos são
  autorizados; um cabo USB-C não elimina essa exigência.
- [`USBDriverKit`](https://developer.apple.com/documentation/usbdriverkit) está
  disponível no iPadOS somente em iPads com chip da série M. Exige um target de
  driver, entitlements concedidos pela Apple, assinatura apropriada e validação
  no hardware.
- DriverKit não transforma o mesmo binário em um driver para iPhone.

Assim, controle físico do ToneX One no iPhone só seria viável com cooperação do
fabricante para MFi/iAP ou com uma ponte externa suportada. Nenhuma dessas rotas
faz parte desta versão.

## Requisitos de desenvolvimento

- Mac compatível com a versão atual do Xcode;
- Xcode com runtime de iOS 17 ou superior;
- [XcodeGen](https://github.com/yonaskolb/XcodeGen);
- Swift incluído no Xcode;
- para dispositivo físico: Apple Account no Xcode;
- para TestFlight/App Store: participação ativa no Apple Developer Program.

O app tem deployment target iOS 17.0, bundle ID padrão
`com.opentonex.tusb` e scheme `TUSBApp`.

## Estrutura

```text
ios/
├── Package.swift
├── project.yml
├── TUSBApp/
│   ├── Resources/
│   └── *.swift
├── TUSBUITests/
├── Sources/
│   └── TUSBCore/
└── Tests/
    └── TUSBCoreTests/
```

`TUSBApp.xcodeproj` é gerado por XcodeGen. Faça mudanças de projeto em
`ios/project.yml` e gere o projeto novamente.

## Build local

Na raiz do repositório:

```bash
cd ios
brew install xcodegen
swift test --enable-code-coverage
xcodegen generate
open TUSBApp.xcodeproj
```

Pela linha de comando, substitua o nome do aparelho se ele não estiver instalado:

```bash
xcrun simctl list devices available

xcodebuild build \
  -project TUSBApp.xcodeproj \
  -scheme TUSBApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO

xcodebuild test \
  -project TUSBApp.xcodeproj \
  -scheme TUSBApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -enableCodeCoverage YES \
  CODE_SIGNING_ALLOWED=NO
```

Windows e Linux podem editar o código, mas não compilam o app iOS nem executam o
iPhone Simulator. O build de app suportado é feito no macOS/Xcode.

## CI

O workflow [`.github/workflows/ios-ci.yml`](../.github/workflows/ios-ci.yml):

1. usa permissões somente de leitura;
2. instala XcodeGen e gera o projeto;
3. executa `swift test --enable-code-coverage`;
4. seleciona dinamicamente um iPhone Simulator disponível;
5. executa `xcodebuild build` e `xcodebuild test` com cobertura;
6. publica logs, `.xcresult` e dados de cobertura por 14 dias.

O workflow não usa certificado, profile, senha ou chave da Apple. Ele não
arquiva nem publica o app.

## Assinatura para aparelho físico

Para desenvolvimento local:

1. gere e abra `ios/TUSBApp.xcodeproj`;
2. selecione o target **TUSBApp**;
3. em **Signing & Capabilities**, escolha sua equipe;
4. use um bundle ID único se `com.opentonex.tusb` já estiver registrado;
5. mantenha **Automatically manage signing** habilitado;
6. conecte e confie no dispositivo, selecione-o como destino e execute.

Uma conta gratuita pode ter limitações de provisionamento. Certificados Apple
Development executam no aparelho; distribuição por TestFlight/App Store requer
Apple Distribution e acesso ao App Store Connect. Consulte a
[visão geral de certificados](https://developer.apple.com/help/account/create-certificates/certificates-overview/).

Não adicione certificados, `.p12`, provisioning profiles, senhas ou chaves do
App Store Connect ao repositório.

## TestFlight e App Store

1. participe do Apple Developer Program;
2. registre o App ID e crie o app no App Store Connect com o mesmo bundle ID;
3. em Xcode, defina equipe, versão e build;
4. selecione **Any iOS Device (arm64)** e use **Product > Archive**;
5. no Organizer, execute **Validate App** e **Distribute App > App Store Connect**;
6. aguarde o processamento e distribua o build no TestFlight;
7. valide em hardware real antes de enviar à revisão;
8. preencha privacidade, classificação etária, screenshots, descrição, suporte,
   política de privacidade e informações de criptografia;
9. selecione o build e envie a versão para App Review.

O TestFlight aceita builds por até 90 dias e builds para testadores externos
podem passar por Beta App Review. Veja
[TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)
e [upload de builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/).

Não descreva controle USB do ToneX One na ficha da loja enquanto essa função não
existir e não tiver sido validada no hardware suportado.

## Diagnóstico de build

| Erro | Ação |
|---|---|
| `xcodegen: command not found` | Execute `brew install xcodegen`. |
| Projeto desatualizado | Execute `cd ios && xcodegen generate`. |
| Simulator não encontrado | Instale um runtime em **Xcode > Settings > Platforms** e confira `xcrun simctl list devices available`. |
| Scheme `TUSBApp` ausente | Gere novamente a partir de `ios/project.yml`. |
| `No profiles for ... were found` | Escolha uma equipe e assinatura automática; para Simulator use `CODE_SIGNING_ALLOWED=NO`. |
| Bundle ID indisponível | Defina um identificador único para sua equipe. |
| Falha apenas no aparelho | Colete o log do Xcode e registre modelo, versão do iOS, cabo/acessório e passos; o CI não cobre hardware. |

## Critério de suporte

Uma função só deve ser marcada como compatível com hardware após teste repetível
em dispositivo físico. Simulator verde significa que UI e lógica passaram; não
é evidência de compatibilidade USB, MIDI, áudio, cabo, hub ou latência.
