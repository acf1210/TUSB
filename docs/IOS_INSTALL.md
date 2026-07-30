# Instalação do TUSB no iPhone e iPad

Esta versão pode ser instalada pelo Xcode para desenvolvimento e, quando um
build assinado for disponibilizado, pelo TestFlight ou App Store. Não existe
IPA oficial para instalação manual.

> **Limite importante:** o app não controla o ToneX One por USB em iPhones.
> iOS não oferece API pública genérica CDC-ACM. `ExternalAccessory` depende de
> autorização MFi do fabricante. `USBDriverKit` só é uma opção no iPad com chip
> M e ainda exige entitlement, driver e validação física.

## Compatibilidade

- iOS/iPadOS 17 ou superior;
- iPhone ou iPad para interface e modo simulado;
- Mac com Xcode para instalação de desenvolvimento;
- Apple Developer Program para TestFlight e App Store.

Não houve validação em dispositivo Apple físico. Consulte a
[matriz de suporte](IOS.md#estado-atual) antes de instalar.

## Opção A: iPhone Simulator

Use esta opção para avaliar a interface e o pedal simulado:

```bash
git clone https://github.com/acf1210/TUSB.git
cd TUSB/ios
brew install xcodegen
swift test
xcodegen generate
open TUSBApp.xcodeproj
```

No Xcode:

1. escolha o scheme **TUSBApp**;
2. selecione um iPhone Simulator com iOS 17 ou superior;
3. pressione **Run** (`⌘R`);
4. no app, use o modo simulado.

O Simulator não acessa o USB do ToneX One e não comprova MIDI, microfone ou
latência reais.

## Opção B: dispositivo físico pelo Xcode

1. conclua os comandos da opção A;
2. conecte o iPhone/iPad ao Mac e confirme **Confiar**;
3. no Xcode, abra **TUSBApp > Signing & Capabilities**;
4. escolha sua equipe e deixe **Automatically manage signing** habilitado;
5. se necessário, troque `com.opentonex.tusb` por um bundle ID único;
6. selecione seu aparelho como destino e pressione `⌘R`;
7. se o iOS solicitar, ative o **Modo de Desenvolvedor** e reinicie o aparelho.

Essa instalação valida apenas a execução do app. Ela não habilita USB CDC-ACM
no iPhone e não adiciona os entitlements de DriverKit.

## Opção C: TestFlight

Esta opção só aparece quando a equipe publica um beta:

1. instale o app **TestFlight** pela App Store;
2. abra o convite recebido por e-mail ou link público;
3. toque em **Aceitar** e depois em **Instalar**;
4. atualize o beta pelo TestFlight;
5. envie feedback pelo TestFlight, incluindo modelo do aparelho, versão do
   sistema e passos para reproduzir.

Builds TestFlight expiram após 90 dias. Se o convite não abrir, confirme que
você usa o Apple Account convidado e que há vagas disponíveis.

## Opção D: App Store

Quando houver uma versão publicada:

1. abra a App Store;
2. procure por **TUSB** e confira o desenvolvedor;
3. verifique os requisitos da versão;
4. toque em **Obter**.

Até existir uma página oficial publicada pela equipe, não instale perfis de
configuração, certificados corporativos ou IPAs oferecidos por terceiros.

## Atualização e remoção

- Xcode: execute novamente com o mesmo bundle ID para substituir a instalação.
- TestFlight: atualize dentro do TestFlight.
- App Store: atualize normalmente pela App Store.
- Remoção: mantenha o ícone pressionado e escolha **Remover App**.

Remover o app também pode apagar preferências e capturas locais. Exporte
qualquer diagnóstico importante antes.

## Problemas comuns

| Sintoma | Solução |
|---|---|
| O Xcode não mostra o aparelho | Desbloqueie-o, confirme **Confiar**, teste outro cabo de dados e confira **Window > Devices and Simulators**. |
| `Developer Mode disabled` | Ative **Ajustes > Privacidade e Segurança > Modo de Desenvolvedor**. |
| Falha de assinatura | Escolha a equipe, use bundle ID único e habilite assinatura automática. |
| App de conta gratuita deixou de abrir | Reconecte ao Xcode e provisione/instale novamente. |
| TestFlight diz que o build expirou | Instale um build beta mais novo; a equipe precisa publicá-lo. |
| O app não encontra o ToneX One no iPhone | É uma limitação esperada: não há transporte CDC-ACM público no iPhone. |
| Um iPad M não reconhece o pedal | Ter chip M não basta; o app precisa de driver USBDriverKit assinado, entitlements e suporte explícito. |

Para falhas do projeto ou de build, consulte
[iOS: desenvolvimento, build e suporte](IOS.md#diagnóstico-de-build).
