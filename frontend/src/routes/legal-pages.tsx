import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";

const UPDATED_AT = "7 de outubro de 2026";
/** Quem responde pelo Gomo e o canal para pedidos sobre dados pessoais, definidos no build (frontend/.env). */
const RESPONSIBLE = import.meta.env.VITE_LEGAL_RESPONSIBLE?.trim();
const CONTACT_EMAIL = import.meta.env.VITE_LEGAL_CONTACT_EMAIL?.trim();

export function PrivacyPage() {
  return (
    <LegalPage title="Política de Privacidade">
      <p>
        Esta política explica quais dados o Gomo guarda sobre você, para quê e como você controla esses dados,
        conforme a Lei Geral de Proteção de Dados (Lei nº 13.709/2018).
      </p>
      <Controller />

      <h2>Dados que guardamos</h2>
      <ul>
        <li><strong>Conta:</strong> nome, e-mail e a confirmação do e-mail. A senha é guardada apenas como hash (BCrypt): ninguém, nem a equipe do Gomo, consegue lê-la.</li>
        <li><strong>O que você cria no app:</strong> listas de compras, alertas de preço e as notificações deles, cidade preferida, mercados favoritos e preços que você enviar como contribuição.</li>
        <li><strong>Plano:</strong> se a conta é grátis ou Premium, as datas do teste grátis e quantas comparações completas você fez no dia (para o limite do plano grátis).</li>
        <li><strong>Sessão:</strong> um código de acesso, guardado no seu navegador e, do nosso lado, só como hash, com validade de 12 horas.</li>
        <li><strong>Segurança:</strong> o endereço IP de quem tenta entrar ou criar conta é usado, apenas na memória do servidor e por no máximo uma hora, para limitar tentativas repetidas. O servidor também mantém registros técnicos de acesso por um período curto, para investigar falhas e abusos.</li>
      </ul>
      <p>Não usamos cookies de publicidade nem ferramentas de rastreamento ou análise de terceiros.</p>

      <h2>Para que usamos</h2>
      <ul>
        <li>Manter sua conta e mostrar suas listas, alertas e comparações (execução do serviço que você contratou, art. 7º, V).</li>
        <li>Enviar os e-mails da conta: confirmação de e-mail e redefinição de senha.</li>
        <li>Proteger o serviço contra abuso e acesso indevido (legítimo interesse, art. 7º, IX).</li>
      </ul>
      <p>Não vendemos nem alugamos seus dados e não os usamos para publicidade.</p>

      <h2>Com quem os dados são compartilhados</h2>
      <p>
        Somente com os fornecedores que fazem o Gomo funcionar: a empresa que hospeda o servidor e o banco de dados
        e o serviço de envio de e-mails. Eles tratam os dados apenas para prestar esse serviço. Os preços exibidos vêm
        dos sites públicos dos supermercados; nenhum dado seu é enviado a eles.
      </p>

      <h2>Por quanto tempo</h2>
      <p>
        Enquanto sua conta existir. Ao excluir a conta, apagamos na hora suas listas, alertas, preferências, sessões e
        o registro de uso. Se você enviou preços como contribuição, eles continuam no histórico de preços, mas a conta
        é anonimizada: sem nome, sem e-mail e sem como entrar nela. Cópias de segurança do banco são mantidas por até
        14 dias e depois descartadas.
      </p>

      <h2>Seus direitos</h2>
      <p>
        Você pode confirmar quais dados temos, corrigi-los, pedir uma cópia, revogar o consentimento e pedir a
        exclusão (art. 18). O nome se corrige em <Link to="/app/perfil">Perfil</Link>, e lá também está o botão
        <strong> Excluir conta</strong>, que faz a exclusão na hora.
        {CONTACT_EMAIL ? <> Para os outros pedidos, escreva para <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.</> : null}
      </p>

      <h2>Mudanças nesta política</h2>
      <p>Se a forma de tratar seus dados mudar, esta página será atualizada e a data no topo mudará.</p>
    </LegalPage>
  );
}

export function TermsPage() {
  return (
    <LegalPage title="Termos de Uso">
      <p>Ao criar uma conta ou usar o Gomo, você concorda com estes termos.</p>
      <Controller />

      <h2>O que é o Gomo</h2>
      <p>
        O Gomo compara preços de supermercados de Volta Redonda e região a partir das informações que os próprios
        mercados publicam em seus sites. O Gomo não vende produtos, não faz entregas e não tem relação comercial com os
        supermercados comparados.
      </p>

      <h2>Sobre os preços</h2>
      <ul>
        <li>Os preços são coletados várias vezes por dia e cada um mostra quando foi visto. Um mercado pode mudar o preço, encerrar uma promoção ou ficar sem o produto entre uma coleta e outra.</li>
        <li>O preço da loja física pode ser diferente do preço publicado no site do mercado.</li>
        <li>Promoções que dependem de clube, aplicativo ou quantidade mínima aparecem separadas e não entram no total.</li>
        <li>Confira o preço no mercado antes de comprar. O Gomo não se responsabiliza por diferenças entre o preço exibido e o cobrado.</li>
      </ul>

      <h2>Sua conta</h2>
      <ul>
        <li>Use um e-mail seu e uma senha só sua; você responde pelo que for feito com a sua conta.</li>
        <li>Preços enviados como contribuição precisam ser verdadeiros. Eles passam por moderação e podem ser recusados; contribuições falsas podem levar ao bloqueio da conta.</li>
        <li>Não é permitido usar o Gomo para coletar dados em massa, sobrecarregar o serviço ou contornar os limites do plano.</li>
      </ul>

      <h2>Planos</h2>
      <p>
        O plano grátis tem limites descritos na página de <Link to="/assinar">planos</Link>. O teste do Premium dura
        7 dias, não pede cartão e, ao terminar, a conta volta sozinha ao plano grátis, sem cobrança.
      </p>

      <h2>Disponibilidade</h2>
      <p>
        Trabalhamos para manter o Gomo no ar e os preços atualizados, mas o serviço pode ficar indisponível ou um
        mercado pode ficar temporariamente sem preços, por exemplo quando muda o próprio site.
      </p>

      <h2>Encerramento</h2>
      <p>Você pode excluir sua conta a qualquer momento em <Link to="/app/perfil">Perfil</Link>. Veja na <Link to="/privacidade">Política de Privacidade</Link> o que acontece com seus dados.</p>

      <h2>Mudanças nestes termos</h2>
      <p>Mudanças importantes serão avisadas no próprio app antes de valer.</p>
    </LegalPage>
  );
}

function Controller() {
  if (!RESPONSIBLE && !CONTACT_EMAIL) return null;
  return (
    <p>
      {RESPONSIBLE ? <>Responsável pelo Gomo e pelo tratamento dos dados: <strong>{RESPONSIBLE}</strong>. </> : null}
      {CONTACT_EMAIL ? <>Contato: <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.</> : null}
    </p>
  );
}

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="min-h-screen bg-[#f7f7f8] px-4 py-10 sm:py-16">
      <article className="legal-page surface mx-auto w-full max-w-3xl p-6 sm:p-10">
        <Link to="/" aria-label="Gomo, página inicial"><BrandLogo /></Link>
        <h1 className="mt-8 text-3xl font-extrabold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm text-muted">Atualizado em {UPDATED_AT}.</p>
        <div className="mt-6">{children}</div>
        <nav aria-label="Documentos" className="mt-10 flex flex-wrap gap-4 border-t border-border pt-6 text-sm font-semibold">
          <Link to="/termos" className="text-primary hover:underline">Termos de Uso</Link>
          <Link to="/privacidade" className="text-primary hover:underline">Política de Privacidade</Link>
          <Link to="/" className="text-muted hover:underline">Voltar ao início</Link>
        </nav>
      </article>
    </main>
  );
}
