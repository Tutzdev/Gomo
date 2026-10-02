import { NativeButton } from "@/components/ui/native-button";

export function ClosingSection({ signUpPath }: { signUpPath: string }) {
  return (
    <section className="landing-closing" aria-labelledby="closing-title">
      <div className="landing-container">
        <div className="landing-closing__panel">
          <h2 id="closing-title" className="landing-closing__title">Monte a próxima lista já sabendo onde comprar.</h2>
          <p className="landing-closing__text">Compare os supermercados da sua cidade antes de sair de casa.</p>
          <div className="landing-closing__actions">
            <NativeButton to={signUpPath} size="lg" variant="secondary" className="landing-button landing-button--on-red">
              Começar grátis
            </NativeButton>
            <NativeButton to="/assinar" size="lg" variant="ghost" className="landing-button landing-button--ghost-on-red">
              Ver planos
            </NativeButton>
          </div>
        </div>
      </div>
    </section>
  );
}
