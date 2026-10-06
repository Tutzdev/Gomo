/*
 * ATENÇÃO: textos de exemplo, escritos para montar o layout. Não são de pessoas reais.
 * Antes de divulgar a página, troque pelos comentários de quem usa o Gomo (com autorização
 * de cada pessoa) e mude TESTIMONIALS_ARE_EXAMPLES para false. Enquanto for true, a seção
 * avisa que os depoimentos são ilustrativos, porque apresentar depoimento inventado como
 * real é propaganda enganosa (CDC, art. 37).
 */
export const TESTIMONIALS_ARE_EXAMPLES = true;

export interface Testimonial {
  name: string;
  context: string;
  text: string;
}

export const testimonialRows: Testimonial[][] = [
  [
    {
      name: "Carla M.",
      context: "Faz a compra do mês para 4 pessoas",
      text: "fiz a lista do mês no domingo e o mercado onde eu sempre ia saía quase 40 reais mais caro. quase 40 reais!! troquei na hora",
    },
    {
      name: "Rafael S.",
      context: "Mora sozinho",
      text: "achava que comparar preço era coisa de quem tem tempo sobrando. leva 2 minutos. o café que eu tomo tava 4 reais mais barato a três quadras de casa",
    },
    {
      name: "Lúcia F.",
      context: "Aposentada",
      text: "Minha neta deixou o Gomo no meu celular. Agora eu olho antes de sair e já vou direto no mercado certo, sem ficar rodando com a sacola.",
    },
    {
      name: "Thiago R.",
      context: "Divide apartamento com dois amigos",
      text: "a gente racha a compra e toda semana era discussão de onde comprar. agora um abre o Gomo, vê qual sai mais barato e acabou a briga kkk",
    },
    {
      name: "Juliana P.",
      context: "Mãe do Theo, de 2 anos",
      text: "Fralda e leite são o que mais pesa aqui. Coloquei alerta nos dois e fico sabendo quando baixa. Esse mês sobrou um pacote de fralda.",
    },
  ],
  [
    {
      name: "Marcos A.",
      context: "Sempre comprou no atacarejo",
      text: "Eu tinha certeza que o atacarejo era o mais barato pra tudo. Não é. Tem coisa que no mercadinho do bairro sai mais em conta, só descobri comparando.",
    },
    {
      name: "Fernanda L.",
      context: "Cuida das compras da casa",
      text: "o que eu mais gosto é que aparece a hora que o preço foi pego. já caí em app que mostrava preço da semana passada",
    },
    {
      name: "Pedro H.",
      context: "Estudante",
      text: "uso o grátis mesmo e já resolve. vejo os 3 mais baratos e pronto, não preciso de mais que isso",
    },
    {
      name: "Ana Beatriz C.",
      context: "Faz bolo por encomenda",
      text: "Compro muito óleo, farinha e açúcar. Centavos de diferença no quilo viram dinheiro no fim do mês. Abro o Gomo antes de toda compra grande.",
    },
    {
      name: "Antônio C.",
      context: "Motorista de aplicativo",
      text: "Passo na frente de uns cinco mercados por dia trabalhando. Agora eu sei em qual vale a pena parar.",
    },
  ],
];
