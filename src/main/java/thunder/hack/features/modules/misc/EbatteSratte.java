public class EbatteSratte extends Module {
    private final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 30);
    private final Setting<Server> server = new Setting<>("Server", Server.FunnyGame);
    private final Setting<Messages> mode = new Setting<>("Mode", Messages.Default);

    private static final String[] WORDS = new String[]{
            "Я TBOЮ MATЬ БЛЯTЬ ПOДVECИЛ НА КОЛ ОНА EБAHAЯ БЛЯДИHA",
            "МАМАШУ ТВOЮ АРМАТУРОЙ С ШИПАМИ ПO ХРЕБТУ ПИЗДИЛ",
            "Я ТВOЕЙ МАТЕРИ ПИЗДAК РАЗОРВAЛ СЫН БЛЯДИHЫ ТЫ ЕБAHHOЙ",
            "ВГEТAЙ ТAНДEРHАК СЫН ЕБAHHOЙ ШЛЮХИ",
            "ТЫ ПСИHА БEЗ БРEЙHА ДAВAЙ ТEРПИ ТEРПИ",
            "я твою мать об стол xуяpил сын тупорылой овчарки мать продал чит на кубики купил?",
            "СКУЛИ СВIHЬЯ ЕБAHAЯ , Я ТВOЮ MATЬ ПOДBECИЛ НА ЦEПЬ И С ОКНА СБРOСИЛ ОНА ФEМИHИСТКА ЕБAHAЯ ОНА СВOИМ ВEСOМ 180КГ ПРOБУРИЛАСЬ ДO ЯДРA ЗEМЛИ И СГOРEЛA HAXУЙ АХAХAХAХA ЕБAТЬ ОНА ГOРИТ ПРИКОЛЬНO",
            "ты мейн сначало свой пукни потом чет овысирай, с основы пиши нищ",
            "БAБКА СДOХЛA ОТ СТAРOСТИ Т.К. КOГДА ТВOЮ MATЬ РOДИЛИ ЕЙ БЫЛO 99 ЛEТ И ОТ НEРВOВ РAДOСТИ ОНА СДOХЛA ОЙ БЛ9TЬ ОТ РAДOСТИ ДEД ТOЖE ОТ РAДOСТИ СДOХ HAXУЙ ДOЛБAЁБ ЕБAHHЫЙ ЧТОБЫ ВЫЖИТЬ НА ПOМOЙКА MATЬ ТВOЯ POKA НE СДOХЛA ЕБAЛAСЬ С МУЖИКАМИ ЗA 2 КOПEЙКИ",
            "ТЫ ПOНИМAЕШЬ ЧТО Я ТВOЮ МAТЬ ОТПРAВИЛ СO СВOЕГO XУЯ В НEБO, ЧТОБ ОНА СВOИМ ПИЗДAKOМ ПРИНИМAЛA МИТEОРИТНУЮ АТАКУ?)",
            "ТЫ ПOНИМAЕШЬ ЧТО ТBОЯ MATЬ СИДИТ У МEНЯ НА ЦEПИ И КAК БУЛЬДOГ ЕБAHHЫЙ НА МOЙ XУЙ СЛЮНИ БЛ9ДЬ ПУСКАЕТ?))",
            "В ДEТДOМЕ ТEБЯ ПИЗДUЛИ ВСE КТО МOГ В ИТОГE ТЫ СДOХ НА УЛИЦE В 13 ЛEТ ОТ НEДOСТAТКА ЕДЫ ВOДУ ТЫ ЖE БPАЛ ЭТИМ ФИЛЬТPОМ И МOЧOЙ ДOЛБAЁБ ЕБAHHЫЙ СУКA БEЗ MATEPHAЯ ХУETA.",
            "Чё как нищий, купи тандерхак не позорься",
            "Your mom owned by Thunderhack Recode",
            "АЛO БOМЖAТИHА БEЗ МAТEРИ Я ТВOЮ МAТЬ ОБ СТОЛ УБИЛ ЧEРEП ЕЙ РAЗБИЛ НOГOЙ БAТЮ ТВOЕГO С ОКНА ВЫКИНУЛ СУКА ЧМO ЕБAHHOЕ ОТВEТЬ ЧМO ЕБЛAН ТВAРЬ ШAЛAВA",
            "1",
            "ГO 1 НА 1 РН СЫН ШЛЮХИ",
            "СКАЖEШЬ - БAТЯ ПИДOР, ПРOМOЛЧИШЬ - МAТЬ ШЛЮХA"
    };

    private static final String[] ULYBAKA1337 = new String[]{
            "маме твоей ебало бил",
            "отсосите мне нежно пж",
            "сука ебало закрой сын ущемленной мрази",
            "зачем я твою мамку убил кроме того что она ебаная шваль",
            "быть нищиебом означает ебать твою нищию мразотную семейку",
            "почему твой папка пидарас",
            "почему твоя мамка божество минета",
            "хахаха сын обиды ебаной",
            "ущемил всю твою мразотную семейку своим членом",
            "почему ваша бабка отсосала всему населению планеты земля",
            "кто мне вчера отсосал если не твоя мать",
            "мой отец убийца зсу, и твоей мразотной семейки азовцев тоже",
            "у тебя папка умирает в окопах!!!",
            "мне мать твоя сосала и пересасывала",
            "что в хуй че с хуя кто батя твой кроме пидараса",
            "затерпи хуи по традиции твоей семейки",
            "сука сын мрази закрой ебало",
            "твою мать ебала черная оргия а ты со слезами на глазах затерпел",
            "устроил золотой дождь твоей мерзкой семейке",
            "кто папка твой кроме мерзкого выблядка",
            "кому сосал кроме меня",
            "отсоси ртом своей мамки мне нежно",
            "как дрочить если не ртом твоей мамки"
    };

    private static final String[] WORDSENG = new String[]{
        "I strung your fucking mother up on a stake the fucking whore.",
        "I BEAT YOUR MOM ACROSS THE SPINE WITH A SPIKED REBAR ROD",
        "I tore your mother's cunt wide open, you son of a fucking whore.",
        "GET THUNDERHACK, YOU FUCKING WHORE'S SON",
        "You brainless mutt, just take it suck it up.",
        "I smashed your mom's face against the table, you son of a brain-dead German Shepherd did you sell your mom to buy a cheat for Cubes?",
        "Squeal, you fucking pig! I strung your mother up on a chain and threw her out the window that fucking feminist—and with her 180kg bulk, she drilled all the way down to the Earth's core and fucking burned up! Hahahaha, holy shit, watching her burn is hilarious.",
        "State your main first before you start spewing shit—post from your main, you broke loser.",
        "The old hag croaked of old age cause she was 99 when your mom was born, and she croaked from the stress of the joy oh, fuck, from the joy and the old man croaked from the joy too, you fucking moron; just to survive, your mom was fucking guys for peanuts before she kicked the bucket.",
        "Do you realize I launched your mother into the sky straight off my cock so she could take a meteor strike right in her cunt?",
        "Do you realize your mother is chained up at my place, drooling over my cock like a fucking bulldog?",
        "In the orphanage, everyone who could beat the shit out of you did; in the end, you died on the street at 13 from starvation you were getting your water from that filter and your own piss, you fucking moron, you piece of shit.",
        "Why are you acting like a broke loser? Buy ThunderHack and stop embarrassing yourself.",
        "Your mom got owned by Thunderhack Recode",
        "HEY YOU MOTHERLESS PIECE OF TRASH I SMASHED YOUR MOTHER'S HEAD AGAINST THE TABLE AND CRUSHED HER SKULL WITH MY FOOT AND THREW YOUR DAD OUT THE WINDOW YOU BITCH YOU FUCKING LOSER ANSWER ME YOU LOSER YOU MORON YOU SCUM YOU SLUT",
        "1",
        "1v1 me right now, you son of a whore",
        "Say it, and your dad's a faggot stay silent, and your mom's a whore."
    };

    private static final String[] ULYBAKA1337Eng = new String[]{
        "I smashed your mom's face in",
        "Suck me off gently, please.",
        "Shut your fucking trap, you son of an oppressed piece of shit",
        "Why did I kill your mom, besides the fact that she's a fucking piece of trash?",
        "Being a broke-ass bum means fucking your scummy, dirt-poor family.",
        "Why is your dad a faggot?",
        "Why is your mom a blowjob goddess?",
        "Hahaha, you son of a fucking grudge.",
        "Slammed my cock into your whole scummy family.",
        "Why did your grandma suck off the entire population of planet Earth?",
        "Who sucked me off yesterday if not your mother?",
        "My father is a killer of the AFU and of your scumbag Azov family, too.",
        "Your dad is dying in the trenches!!!",
        "Your mother sucked me offand kept right on sucking.",
        "What the fuck, what the hell, who's your dad besides a faggot?",
        "Suck some dicks, following your family's tradition.",
        "You son of a bitch, you piece of scum shut your fucking mouth.",
        "A black orgy fucked your mother, and you just took it with tears in your eyes.",
        "rained a golden shower on your vile family",
        "Who's your daddy, besides that vile bastard?",
        "Who else did you suck off besides me?",
        "Suck me off gently with your mom's mouth.",
        "How are you supposed to jerk off if not with your mom's mouth?"
    };

    private final Timer timer = new Timer();
    private ArrayList<String> words = new ArrayList<>();

    public EbatteSratte() {
        super("EbatteSratte", Module.Category.MISC);
        loadEZ();
    }

    @Override
    public void onEnable() {
        loadEZ();
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onAttackEntity(@NotNull EventAttack event) {
        if (event.getEntity() instanceof PlayerEntity && !event.isPre()) {
            if (timer.passedS(delay.getValue())) {
                PlayerEntity entity = (PlayerEntity) event.getEntity();
                if (entity == null) return;

                int n;

                if (words.isEmpty() && mode.getValue() == Messages.Custom) {
                    disable();
                    return;
                }

                String[] cartel = isRu() ? (mode.getValue() == Messages.Default ? WORDS : (mode.getValue() == Messages.UlybakaHuevo ? ULYBAKA1337 : words.toArray(new String[0]))) : (mode.getValue() == Messages.Default ? WORDSENG : (mode.getValue() == Messages.UlybakaHuevo ? ULYBAKA1337Eng : words.toArray(new String[0])));

                if (mode.getValue() == Messages.Default) {
                    n = (int) Math.floor(Math.random() * cartel.length);
                } else if (mode.getValue() == Messages.UlybakaHuevo) {
                    n = (int) Math.floor(Math.random() * cartel.length);
                } else {
                    n = (int) Math.floor(Math.random() * words.size());
                }

                String chatPrefix = switch (server.getValue()) {
                    case FunnyGame -> "!";
                    case OldServer -> ">";
                    case DirectMessage -> "/msg ";
                    case Local -> "";
                };

                if (chatPrefix.contains("/"))
                    mc.getNetworkHandler().sendChatCommand("/msg " + entity.getName().getString() + " " + cartel[n]);
                else
                    mc.getNetworkHandler().sendChatMessage(chatPrefix + entity.getName().getString() + " " + cartel[n]);

                timer.reset();
            }
        }
    }

    public void loadEZ() {
        try {
            File file = new File("ThunderHackRecode/misc/EbatteSratte.txt");
            if (!file.exists() && !file.createNewFile())
                sendMessage("Error with creating file");

            new Thread(() -> {
                try {
                    FileInputStream fis = new FileInputStream(file);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(isr);
                    ArrayList<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    boolean newline = false;
                    for (String l : lines) {
                        if (l.isEmpty()) {
                            newline = true;
                            break;
                        }
                    }
                    words.clear();
                    ArrayList<String> spamList = new ArrayList<>();
                    if (newline) {
                        StringBuilder spamChunk = new StringBuilder();
                        for (String l : lines) {
                            if (l.isEmpty()) {
                                if (!spamChunk.isEmpty()) {
                                    spamList.add(spamChunk.toString());
                                    spamChunk = new StringBuilder();
                                }
                            } else spamChunk.append(l).append(" ");
                        }
                        spamList.add(spamChunk.toString());
                    } else spamList.addAll(lines);

                    words = spamList;
                } catch (Exception ignored) {
                }
            }).start();
        } catch (IOException ignored) {
        }
    }

    public enum Server {
        FunnyGame,
        DirectMessage,
        OldServer,
        Local
    }

    public enum Messages {
        Default,
        UlybakaHuevo,
        Custom
    }
}