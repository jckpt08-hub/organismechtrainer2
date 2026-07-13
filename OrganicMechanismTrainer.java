
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ravenclaw-inspired Organic Mechanism Trainer.
 * Java 17+ / Swing / no external runtime dependencies.
 */
public class OrganicMechanismTrainer extends JFrame {
    private static final String APP_TITLE = "THE ARCANE ORGANIC ARCHIVE";
    private static final Path DATA_DIR = Paths.get(System.getProperty("user.home"), ".organic_mechanism_trainer");
    private static final Path CUSTOM_FILE = DATA_DIR.resolve("custom_compounds.tsv");

    private static final Color NIGHT = new Color(18, 10, 31);
    private static final Color DEEP_PURPLE = new Color(31, 15, 53);
    private static final Color ROYAL_PURPLE = new Color(67, 34, 104);
    private static final Color VIOLET = new Color(91, 54, 133);
    private static final Color SILVER = new Color(205, 209, 220);
    private static final Color SILVER_DARK = new Color(127, 134, 151);
    private static final Color INK = new Color(38, 29, 49);
    private static final Color PARCHMENT = new Color(247, 244, 249);
    private static final Color MIST = new Color(229, 225, 236);

    private static final String HOME = "home";
    private static final String ADD = "add";
    private static final String QUIZ = "quiz";
    private static final String SCOPE = "scope";

    private final List<ReactionItem> builtInItems = createBuiltInItems();
    private final List<ReactionItem> customItems = new ArrayList<>();
    private final Random random = new Random();
    private final CardLayout navigator = new CardLayout();
    private final JPanel screenHost = new JPanel(navigator);

    private JComboBox<QuizMode> modeCombo;
    private JComboBox<String> categoryCombo;
    private JLabel questionNumberLabel;
    private JLabel categoryLabel;
    private JTextArea promptArea;
    private JTextArea answerArea;
    private JTextArea feedbackArea;
    private ReactionItem currentItem;
    private QuizMode currentMode;
    private int questionCount = 0;

    private SyllabusTableModel syllabusTableModel;
    private CustomTableModel customTableModel;
    private JTable customTable;
    private JLabel scopeCountLabel;
    private JTextArea mechanismScopeArea;

    public OrganicMechanismTrainer() {
        super(APP_TITLE);
        loadCustomItems();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 760));
        setSize(1280, 860);
        setLocationRelativeTo(null);
        setContentPane(buildRootPanel());
        nextQuestion();
    }

    private JComponent buildRootPanel() {
        screenHost.setBackground(NIGHT);
        screenHost.add(buildHomeScreen(), HOME);
        screenHost.add(buildSectionScreen("ADD CHAMBER", "새 화합물과 반응을 문제은행에 새깁니다.", buildAddPanel()), ADD);
        screenHost.add(buildSectionScreen("THE GREAT HALL", "합성 경로와 반응 메커니즘을 시험합니다.", buildQuizPanel()), QUIZ);
        screenHost.add(buildSectionScreen("THE SILVER ARCHIVE", "현재 시험범위에 반영된 화합물과 메커니즘입니다.", buildScopePanel()), SCOPE);
        return screenHost;
    }

    private JComponent buildHomeScreen() {
        GothicHallPanel hall = new GothicHallPanel();
        hall.setLayout(new BorderLayout(30, 20));
        hall.setBorder(new EmptyBorder(42, 55, 40, 55));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel crest = new JLabel("✦  O R G A N I C A   A R C A N A  ✦", SwingConstants.CENTER);
        crest.setAlignmentX(Component.CENTER_ALIGNMENT);
        crest.setForeground(SILVER);
        crest.setFont(new Font(Font.SERIF, Font.BOLD, 17));

        JLabel title = new JLabel("유기화학 메커니즘의 문", SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SERIF, Font.BOLD, 42));

        JLabel subtitle = new JLabel("세 개의 문 중 하나를 선택하십시오", SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(new Color(194, 184, 207));
        subtitle.setFont(new Font(Font.SERIF, Font.PLAIN, 17));

        titleBox.add(crest);
        titleBox.add(Box.createVerticalStrut(12));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(8));
        titleBox.add(subtitle);
        hall.add(titleBox, BorderLayout.NORTH);

        JPanel doors = new JPanel(new GridLayout(1, 3, 38, 0));
        doors.setOpaque(false);
        doors.setBorder(new EmptyBorder(6, 15, 0, 15));

        GothicDoorButton addDoor = new GothicDoorButton("ADD", "화합물 추가", "새로운 이름과 반응을\n문제은행에 등록");
        GothicDoorButton quizDoor = new GothicDoorButton("QUIZ", "문제 풀러 가기", "무작위 합성·예측\n메커니즘 훈련");
        GothicDoorButton scopeDoor = new GothicDoorButton("SCOPE", "시험범위", "화합물과 모든\n반응 메커니즘 열람");

        addDoor.addActionListener(e -> showScreen(ADD));
        quizDoor.addActionListener(e -> showScreen(QUIZ));
        scopeDoor.addActionListener(e -> {
            refreshSyllabus();
            showScreen(SCOPE);
        });

        doors.add(addDoor);
        doors.add(quizDoor);
        doors.add(scopeDoor);
        hall.add(doors, BorderLayout.CENTER);

        JLabel footer = new JLabel("30 BASIC COMPOUNDS  •  CUSTOM ENTRIES  •  SYNTHESIS & PREDICTION", SwingConstants.CENTER);
        footer.setForeground(SILVER_DARK);
        footer.setFont(new Font(Font.SERIF, Font.PLAIN, 13));
        hall.add(footer, BorderLayout.SOUTH);
        return hall;
    }

    private JComponent buildSectionScreen(String title, String subtitle, JComponent content) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(NIGHT);

        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBackground(DEEP_PURPLE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SILVER_DARK),
                new EmptyBorder(16, 22, 16, 24)));

        JButton back = silverButton("←  현관으로");
        back.addActionListener(e -> showScreen(HOME));
        header.add(back, BorderLayout.WEST);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font(Font.SERIF, Font.BOLD, 24));
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(new Color(194, 184, 207));
        subtitleLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 13));
        labels.add(titleLabel);
        labels.add(Box.createVerticalStrut(2));
        labels.add(subtitleLabel);
        header.add(labels, BorderLayout.CENTER);

        JLabel sigil = new JLabel("◇", SwingConstants.RIGHT);
        sigil.setForeground(SILVER);
        sigil.setFont(new Font(Font.SERIF, Font.BOLD, 36));
        header.add(sigil, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private void showScreen(String name) {
        navigator.show(screenHost, name);
    }

    private JPanel buildQuizPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(NIGHT);
        panel.setBorder(new EmptyBorder(18, 22, 22, 22));

        JPanel controls = gothicPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        controls.add(gothicLabel("출제 방식"));
        modeCombo = new JComboBox<>(QuizMode.values());
        styleCombo(modeCombo);
        modeCombo.setPreferredSize(new Dimension(265, 34));
        controls.add(modeCombo);
        controls.add(Box.createHorizontalStrut(14));
        controls.add(gothicLabel("반응 유형"));
        categoryCombo = new JComboBox<>();
        styleCombo(categoryCombo);
        categoryCombo.setPreferredSize(new Dimension(255, 34));
        refreshCategoryCombo();
        controls.add(categoryCombo);
        JButton newButton = purpleButton("새 문제");
        newButton.addActionListener(e -> nextQuestion());
        controls.add(newButton);
        panel.add(controls, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel qMeta = new JPanel(new BorderLayout());
        qMeta.setOpaque(false);
        questionNumberLabel = new JLabel("문제 1");
        questionNumberLabel.setForeground(Color.WHITE);
        questionNumberLabel.setFont(new Font(Font.SERIF, Font.BOLD, 21));
        categoryLabel = new JLabel(" ");
        categoryLabel.setForeground(SILVER);
        categoryLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        qMeta.add(questionNumberLabel, BorderLayout.WEST);
        qMeta.add(categoryLabel, BorderLayout.EAST);
        content.add(qMeta);
        content.add(Box.createVerticalStrut(9));

        promptArea = textArea(false, 7, 80);
        promptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        content.add(lightCard("문제", new JScrollPane(promptArea)));
        content.add(Box.createVerticalStrut(12));

        answerArea = textArea(true, 9, 80);
        answerArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        answerArea.setToolTipText("반응물, 조건, 생성물, 화살표 이동을 포함해 자유롭게 서술하세요.");
        content.add(lightCard("내 답안", new JScrollPane(answerArea)));
        content.add(Box.createVerticalStrut(12));

        feedbackArea = textArea(false, 8, 80);
        feedbackArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        content.add(lightCard("핵심어 점검 / 모범답안", new JScrollPane(feedbackArea)));

        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.getViewport().setOpaque(false);
        contentScroll.setOpaque(false);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(contentScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        JButton checkButton = silverButton("핵심어 점검");
        checkButton.addActionListener(this::checkKeywords);
        JButton revealButton = silverButton("모범답안 공개");
        revealButton.addActionListener(e -> revealAnswer());
        JButton nextButton = purpleButton("다음 문제  →");
        nextButton.addActionListener(e -> nextQuestion());
        buttons.add(checkButton);
        buttons.add(revealButton);
        buttons.add(nextButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAddPanel() {
        JPanel outer = new JPanel(new BorderLayout(14, 14));
        outer.setBackground(NIGHT);
        outer.setBorder(new EmptyBorder(18, 22, 22, 22));

        JTextField nameField = styledTextField();
        JTextField formulaField = styledTextField();
        JComboBox<String> reactionTypeCombo = new JComboBox<>(reactionCategories().toArray(new String[0]));
        reactionTypeCombo.setEditable(true);
        styleCombo(reactionTypeCombo);
        JTextField reactantsField = styledTextField();
        JTextField conditionsField = styledTextField();
        JTextArea mechanismField = textArea(true, 7, 42);
        JTextField keywordsField = styledTextField();

        JPanel form = gothicPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER_DARK),
                new EmptyBorder(18, 20, 20, 20)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        int row = 0;
        row = addFormRow(form, c, row, "화합물명 *", nameField);
        row = addFormRow(form, c, row, "분자식/축약식", formulaField);
        row = addFormRow(form, c, row, "반응 유형", reactionTypeCombo);
        row = addFormRow(form, c, row, "합성 반응물", reactantsField);
        row = addFormRow(form, c, row, "반응 조건", conditionsField);
        row = addFormRow(form, c, row, "모범 메커니즘", new JScrollPane(mechanismField));
        row = addFormRow(form, c, row, "핵심어(쉼표 구분)", keywordsField);

        JLabel note = new JLabel("이름만 입력해도 합성 설계 문제에 반영됩니다. 반응물과 메커니즘까지 입력하면 생성물 예측에도 반영됩니다.");
        note.setForeground(new Color(202, 193, 214));
        note.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(note, c);

        JButton addButton = purpleButton("문제은행에 추가");
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        form.add(addButton, c);

        addButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "화합물명은 반드시 입력해야 합니다.", "입력 확인", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String formula = formulaField.getText().trim();
            String category = Objects.toString(reactionTypeCombo.getEditor().getItem(), "사용자 추가").trim();
            if (category.isEmpty()) category = "사용자 추가";
            String reactants = reactantsField.getText().trim();
            String conditions = conditionsField.getText().trim();
            String mechanism = mechanismField.getText().trim();
            if (mechanism.isEmpty()) mechanism = "사용자가 직접 가능한 합성 경로와 전자 이동 메커니즘을 설계한다.";
            List<String> keywords = splitKeywords(keywordsField.getText());
            if (keywords.isEmpty()) keywords = defaultKeywords(name, category, reactants);

            customItems.add(new ReactionItem(name, formula, category, reactants, conditions, name, mechanism, keywords, true));
            saveCustomItems();
            refreshAllModels();

            nameField.setText("");
            formulaField.setText("");
            reactantsField.setText("");
            conditionsField.setText("");
            mechanismField.setText("");
            keywordsField.setText("");
            JOptionPane.showMessageDialog(this, "추가되었습니다. 문제와 시험범위에 즉시 반영됩니다.", "저장 완료", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel formWrap = new JPanel(new BorderLayout(0, 10));
        formWrap.setOpaque(false);
        JLabel formTitle = sectionTitle("새 항목 등록");
        formWrap.add(formTitle, BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getVerticalScrollBar().setUnitIncrement(15);
        formWrap.add(formScroll, BorderLayout.CENTER);

        customTableModel = new CustomTableModel();
        customTable = new JTable(customTableModel);
        styleTable(customTable);
        JButton deleteButton = silverButton("선택한 사용자 항목 삭제");
        deleteButton.addActionListener(e -> deleteSelectedCustomItem());

        JPanel customWrap = new JPanel(new BorderLayout(0, 10));
        customWrap.setOpaque(false);
        JPanel customHead = new JPanel(new BorderLayout());
        customHead.setOpaque(false);
        customHead.add(sectionTitle("사용자 추가 목록"), BorderLayout.WEST);
        customHead.add(deleteButton, BorderLayout.EAST);
        customWrap.add(customHead, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(customTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(SILVER_DARK));
        customWrap.add(tableScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formWrap, customWrap);
        split.setResizeWeight(0.58);
        split.setDividerSize(8);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        outer.add(split, BorderLayout.CENTER);

        JLabel pathNote = new JLabel("저장 위치: " + CUSTOM_FILE);
        pathNote.setForeground(SILVER_DARK);
        pathNote.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        outer.add(pathNote, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildScopePanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(NIGHT);
        panel.setBorder(new EmptyBorder(18, 22, 22, 22));

        scopeCountLabel = new JLabel();
        scopeCountLabel.setForeground(Color.WHITE);
        scopeCountLabel.setFont(new Font(Font.SERIF, Font.BOLD, 18));
        panel.add(scopeCountLabel, BorderLayout.NORTH);

        syllabusTableModel = new SyllabusTableModel();
        JTable scopeTable = new JTable(syllabusTableModel);
        styleTable(scopeTable);
        scopeTable.setAutoCreateRowSorter(true);
        scopeTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        scopeTable.getColumnModel().getColumn(1).setPreferredWidth(170);
        scopeTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        scopeTable.getColumnModel().getColumn(3).setPreferredWidth(190);
        JScrollPane tableScroll = new JScrollPane(scopeTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(SILVER_DARK));

        mechanismScopeArea = textArea(false, 24, 52);
        mechanismScopeArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        JScrollPane mechanismScroll = new JScrollPane(mechanismScopeArea);
        mechanismScroll.setBorder(BorderFactory.createLineBorder(SILVER_DARK));

        JPanel compounds = new JPanel(new BorderLayout(0, 8));
        compounds.setOpaque(false);
        compounds.add(sectionTitle("시험범위 화합물"), BorderLayout.NORTH);
        compounds.add(tableScroll, BorderLayout.CENTER);

        JPanel mechanisms = new JPanel(new BorderLayout(0, 8));
        mechanisms.setOpaque(false);
        mechanisms.add(sectionTitle("시험범위 메커니즘"), BorderLayout.NORTH);
        mechanisms.add(mechanismScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, compounds, mechanisms);
        split.setResizeWeight(0.57);
        split.setDividerSize(8);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        panel.add(split, BorderLayout.CENTER);

        JLabel note = new JLabel("Add Chamber에서 등록하거나 삭제한 내용은 이 목록과 출제 범위에 즉시 반영됩니다.");
        note.setForeground(new Color(202, 193, 214));
        note.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(note, BorderLayout.SOUTH);
        refreshSyllabus();
        return panel;
    }

    private void refreshAllModels() {
        if (customTableModel != null) customTableModel.fireTableDataChanged();
        if (syllabusTableModel != null) syllabusTableModel.fireTableDataChanged();
        refreshCategoryCombo();
        refreshSyllabus();
    }

    private void refreshSyllabus() {
        if (scopeCountLabel != null) {
            scopeCountLabel.setText("기본 30개 + 사용자 추가 " + customItems.size() + "개  |  총 " + allItems().size() + "개 화합물");
        }
        if (syllabusTableModel != null) syllabusTableModel.fireTableDataChanged();
        if (mechanismScopeArea == null) return;

        LinkedHashMap<String, String> catalog = mechanismCatalog();
        StringBuilder text = new StringBuilder();
        int index = 1;
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            text.append(index++).append(". ").append(entry.getKey()).append("\n")
                    .append("   ").append(entry.getValue()).append("\n\n");
        }

        LinkedHashSet<String> customOnlyCategories = customItems.stream()
                .map(ReactionItem::category)
                .filter(category -> !catalog.containsKey(category))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String category : customOnlyCategories) {
            text.append(index++).append(". ").append(category).append(" [사용자 추가]\n")
                    .append("   이 범주의 사용자 등록 항목에 입력된 모범 메커니즘을 출제 기준으로 사용합니다.\n\n");
        }

        if (!customItems.isEmpty()) {
            text.append("──────── 사용자 추가 세부 메커니즘 ────────\n\n");
            for (ReactionItem item : customItems) {
                text.append("• ").append(item.product).append(" — ").append(item.category).append("\n")
                        .append("  ").append(item.mechanism).append("\n\n");
            }
        }
        mechanismScopeArea.setText(text.toString());
        mechanismScopeArea.setCaretPosition(0);
    }

    private LinkedHashMap<String, String> mechanismCatalog() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("나이트로화 EAS", "NO₂⁺ 생성 → 벤젠 π 전자의 공격 → σ-복합체 → 탈양성자화와 방향족성 회복");
        m.put("설폰화 EAS", "SO₃ 계열 친전자체의 공격 → σ-복합체 → 탈양성자화; 가역 반응과 탈설폰화 가능");
        m.put("할로젠화 EAS", "Lewis 산으로 X₂ 활성화 → 방향족 고리 공격 → σ-복합체 → 촉매 재생");
        m.put("프리델–크래프츠 아실화", "아실륨 이온 생성 → 방향족 친전자성 치환 → 가수분해; 일반적으로 재배열 없음");
        m.put("프리델–크래프츠 알킬화", "할로젠화알킬 활성화 → 탄소 친전자체 공격 → 방향족성 회복; 재배열·과알킬화 고려");
        m.put("SN2", "친핵체의 뒤쪽 공격과 이탈기 이탈이 동시에 일어나는 이분자 단일 단계 치환");
        m.put("SN1", "이탈기 이온화 → 카보양이온 → 친핵체 공격 → 필요 시 탈양성자화");
        m.put("E2", "강염기의 β-수소 제거, C=C 형성, 이탈기 이탈이 한 단계에서 동시 진행");
        m.put("E1", "이탈기 이온화와 카보양이온 형성 후 β-수소 제거로 알켄 형성");
        m.put("알데하이드·케톤 수소화", "금속 촉매에서 H₂가 활성화되어 C=O가 각각 1차·2차 알코올로 환원");
        m.put("카복실산·에스터 환원", "LiAlH₄ 수소화물 전달 → 중간체 붕괴/추가 환원 → 산성 처리로 1차 알코올");
        m.put("그리냐르 반응", "RMgX의 탄소 친핵체가 카보닐 공격 → 새 C–C 결합과 알콕사이드 → 산성 처리");
        m.put("알코올의 산 촉매 에터 형성", "OH 양성자화 → 다른 알코올의 친핵성 공격 → 물 이탈 → 에터 형성");
        m.put("피셔 에스터화", "카보닐 양성자화 → 알코올 첨가 → 사면체 중간체 → 물 이탈 → 촉매 재생");
        m.put("알코올 + HX", "OH를 양성자화해 물로 만든 뒤 기질에 따라 SN1 또는 SN2로 할로젠화알킬 형성");
        m.put("알코올 탈수", "OH 양성자화 후 β-수소 제거와 물 이탈로 알켄 형성; 기질에 따라 E1/E2형");
        m.put("알코올 산화", "1차 알코올은 알데하이드/카복실산, 2차 알코올은 케톤으로 산화");
        m.put("에터 산 촉매 분해", "에터 산소 양성자화 후 HX의 X⁻가 SN2 또는 3차 카보양이온 경유 SN1로 C–O 절단");
        m.put("에폭사이드 염기성 고리 열림", "강한 친핵체가 덜 치환된 탄소를 SN2 뒤쪽 공격 → 알콕사이드 → 양성자화");
        m.put("에폭사이드 산성 고리 열림", "에폭사이드 양성자화 → 친핵체가 더 치환된 탄소를 우선 공격 → 탈양성자화");
        return m;
    }

    private void nextQuestion() {
        if (modeCombo == null || categoryCombo == null) return;
        List<ReactionItem> pool = filteredPool();
        if (pool.isEmpty()) {
            JOptionPane.showMessageDialog(this, "선택한 조건으로 출제할 수 있는 항목이 없습니다.", "출제 불가", JOptionPane.WARNING_MESSAGE);
            return;
        }

        QuizMode selected = (QuizMode) modeCombo.getSelectedItem();
        currentMode = selected == QuizMode.RANDOM
                ? (random.nextBoolean() ? QuizMode.SYNTHESIS : QuizMode.PREDICTION)
                : selected;

        if (currentMode == QuizMode.PREDICTION) {
            List<ReactionItem> predictable = pool.stream().filter(ReactionItem::canPredict).toList();
            if (predictable.isEmpty()) {
                JOptionPane.showMessageDialog(this, "반응물 정보가 입력된 항목이 없어 합성 설계 모드로 전환합니다.", "모드 전환", JOptionPane.INFORMATION_MESSAGE);
                currentMode = QuizMode.SYNTHESIS;
            } else {
                pool = predictable;
            }
        }

        ReactionItem next;
        do {
            next = pool.get(random.nextInt(pool.size()));
        } while (pool.size() > 1 && next == currentItem);
        currentItem = next;
        questionCount++;

        questionNumberLabel.setText("문제 " + questionCount + " · " + currentMode.label);
        categoryLabel.setText(currentItem.category + (currentItem.custom ? " · 사용자 추가" : ""));
        promptArea.setText(buildPrompt(currentItem, currentMode));
        promptArea.setCaretPosition(0);
        answerArea.setText("");
        feedbackArea.setText("답안을 작성한 뒤 ‘핵심어 점검’ 또는 ‘모범답안 공개’를 누르세요.");
    }

    private List<ReactionItem> filteredPool() {
        String selectedCategory = Objects.toString(categoryCombo.getSelectedItem(), "전체");
        return allItems().stream()
                .filter(item -> selectedCategory.equals("전체") || item.category.equals(selectedCategory))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String buildPrompt(ReactionItem item, QuizMode mode) {
        if (mode == QuizMode.SYNTHESIS) {
            return "다음 목표 화합물을 합성할 수 있는 대표적인 반응 경로를 제시하세요.\n\n"
                    + "목표 생성물: " + item.product
                    + (item.formula.isBlank() ? "" : "\n분자식/축약식: " + item.formula)
                    + "\n\n작성 항목\n"
                    + "① 출발물질과 시약\n② 반응 조건\n③ 중간체와 전자 이동\n④ 생성물 형성 단계";
        }
        return "다음 반응의 주생성물과 메커니즘을 작성하세요.\n\n"
                + "반응물: " + item.reactants
                + "\n조건: " + (item.conditions.isBlank() ? "조건 미제시" : item.conditions)
                + "\n\n작성 항목\n"
                + "① 주생성물의 구조 또는 명명\n② 반응 유형\n③ 결합 생성·절단과 전자 이동\n④ 위치선택성 또는 재배열 여부";
    }

    private void checkKeywords(ActionEvent event) {
        if (currentItem == null) return;
        String answer = normalize(answerArea.getText());
        if (answer.isBlank()) {
            feedbackArea.setText("먼저 답안을 작성하세요.");
            return;
        }
        List<String> expected = new ArrayList<>(currentItem.keywords);
        if (currentMode == QuizMode.PREDICTION) expected.add(currentItem.product);
        expected = expected.stream().filter(s -> !s.isBlank()).distinct().toList();

        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : expected) {
            if (containsFlexible(answer, keyword)) found.add(keyword);
            else missing.add(keyword);
        }
        int score = expected.isEmpty() ? 0 : (int) Math.round(100.0 * found.size() / expected.size());
        feedbackArea.setText("핵심어 포함률: " + score + "%\n\n"
                + "확인된 핵심어: " + (found.isEmpty() ? "없음" : String.join(", ", found)) + "\n\n"
                + "빠졌을 가능성이 있는 핵심어: " + (missing.isEmpty() ? "없음" : String.join(", ", missing)) + "\n\n"
                + "※ 문자열 기반 보조 점검이며 실제 화학적 타당성을 완전히 판정하지는 않습니다.");
        feedbackArea.setCaretPosition(0);
    }

    private void revealAnswer() {
        if (currentItem == null) return;
        String answer;
        if (currentMode == QuizMode.SYNTHESIS) {
            answer = "[대표 합성 경로]\n"
                    + (currentItem.reactants.isBlank() ? "자유 설계 항목: 가능한 출발물질을 직접 선택합니다." : currentItem.reactants)
                    + (currentItem.conditions.isBlank() ? "" : "\n\n[조건]\n" + currentItem.conditions)
                    + "\n\n[생성물]\n" + currentItem.product
                    + "\n\n[핵심 메커니즘]\n" + currentItem.mechanism;
        } else {
            answer = "[주생성물]\n" + currentItem.product
                    + (currentItem.formula.isBlank() ? "" : " (" + currentItem.formula + ")")
                    + "\n\n[반응 유형]\n" + currentItem.category
                    + "\n\n[핵심 메커니즘]\n" + currentItem.mechanism;
        }
        feedbackArea.setText(answer);
        feedbackArea.setCaretPosition(0);
    }

    private void deleteSelectedCustomItem() {
        if (customTable == null) return;
        int viewRow = customTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "삭제할 사용자 추가 항목을 선택하세요.", "선택 필요", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = customTable.convertRowIndexToModel(viewRow);
        ReactionItem selected = customItems.get(modelRow);
        int choice = JOptionPane.showConfirmDialog(this,
                "‘" + selected.product + "’ 항목을 삭제할까요?",
                "삭제 확인",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            customItems.remove(selected);
            saveCustomItems();
            refreshAllModels();
        }
    }

    private List<ReactionItem> allItems() {
        List<ReactionItem> all = new ArrayList<>(builtInItems);
        all.addAll(customItems);
        return all;
    }

    private void refreshCategoryCombo() {
        if (categoryCombo == null) return;
        String previous = Objects.toString(categoryCombo.getSelectedItem(), "전체");
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("전체");
        allItems().stream().map(item -> item.category).sorted().forEach(categories::add);
        categoryCombo.setModel(new DefaultComboBoxModel<>(categories.toArray(new String[0])));
        categoryCombo.setSelectedItem(categories.contains(previous) ? previous : "전체");
    }

    private Set<String> reactionCategories() {
        return new LinkedHashSet<>(List.of(
                "나이트로화 EAS", "설폰화 EAS", "할로젠화 EAS",
                "프리델–크래프츠 아실화", "프리델–크래프츠 알킬화",
                "SN2", "SN1", "E2", "E1",
                "알데하이드·케톤 수소화", "카복실산·에스터 환원",
                "그리냐르 반응", "알코올의 산 촉매 에터 형성",
                "피셔 에스터화", "알코올 + HX", "알코올 탈수",
                "알코올 산화", "에터 산 촉매 분해",
                "에폭사이드 염기성 고리 열림", "에폭사이드 산성 고리 열림",
                "사용자 추가"
        ));
    }

    private int addFormRow(JPanel panel, GridBagConstraints c, int row, String labelText, Component component) {
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel label = gothicLabel(labelText);
        panel.add(label, c);

        c.gridx = 1;
        c.weightx = 1;
        if (component instanceof JScrollPane) {
            c.fill = GridBagConstraints.BOTH;
            c.weighty = 1;
        } else {
            c.fill = GridBagConstraints.HORIZONTAL;
        }
        panel.add(component, c);
        return row + 1;
    }

    private JPanel gothicPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(DEEP_PURPLE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER_DARK),
                new EmptyBorder(5, 8, 5, 8)));
        return panel;
    }

    private JLabel gothicLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(SILVER);
        label.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        return label;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel("◇  " + text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SERIF, Font.BOLD, 18));
        return label;
    }

    private JPanel lightCard(String titleText, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(PARCHMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER, 2),
                new EmptyBorder(11, 13, 13, 13)));
        JLabel title = new JLabel(titleText);
        title.setForeground(INK);
        title.setFont(new Font(Font.SERIF, Font.BOLD, 15));
        panel.add(title, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JTextField styledTextField() {
        JTextField field = new JTextField();
        field.setBackground(PARCHMENT);
        field.setForeground(INK);
        field.setCaretColor(INK);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER_DARK),
                new EmptyBorder(6, 8, 6, 8)));
        return field;
    }

    private JTextArea textArea(boolean editable, int rows, int cols) {
        JTextArea area = new JTextArea(rows, cols);
        area.setEditable(editable);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(PARCHMENT);
        area.setForeground(INK);
        area.setCaretColor(INK);
        area.setSelectionColor(new Color(165, 145, 191));
        area.setBorder(new EmptyBorder(8, 9, 8, 9));
        return area;
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setBackground(PARCHMENT);
        combo.setForeground(INK);
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        combo.setBorder(BorderFactory.createLineBorder(SILVER_DARK));
    }

    private void styleTable(JTable table) {
        table.setRowHeight(29);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        table.setForeground(INK);
        table.setBackground(PARCHMENT);
        table.setGridColor(new Color(207, 201, 216));
        table.setSelectionBackground(new Color(188, 173, 207));
        table.setSelectionForeground(INK);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(DEEP_PURPLE);
        table.getTableHeader().setForeground(SILVER);
        table.getTableHeader().setFont(new Font(Font.SERIF, Font.BOLD, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(new EmptyBorder(0, 6, 0, 6));
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(renderer);
    }

    private JButton purpleButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBackground(ROYAL_PURPLE);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER_DARK),
                new EmptyBorder(8, 15, 8, 15)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton silverButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SERIF, Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBackground(SILVER);
        button.setForeground(DEEP_PURPLE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SILVER_DARK),
                new EmptyBorder(7, 14, 7, 14)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void loadCustomItems() {
        customItems.clear();
        if (!Files.exists(CUSTOM_FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(CUSTOM_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    customItems.add(decodeItem(line));
                } catch (RuntimeException ignored) {
                    // Skip only the damaged line and continue loading the rest.
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "사용자 문제은행을 불러오지 못했습니다: " + e.getMessage(), "불러오기 오류", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveCustomItems() {
        try {
            Files.createDirectories(DATA_DIR);
            try (BufferedWriter writer = Files.newBufferedWriter(CUSTOM_FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (ReactionItem item : customItems) {
                    writer.write(encodeItem(item));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "사용자 문제은행을 저장하지 못했습니다: " + e.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String encodeItem(ReactionItem item) {
        List<String> fields = List.of(
                item.product, item.formula, item.category, item.reactants,
                item.conditions, item.product, item.mechanism,
                String.join("\u001F", item.keywords)
        );
        return fields.stream().map(OrganicMechanismTrainer::b64).collect(Collectors.joining("\t"));
    }

    private ReactionItem decodeItem(String line) {
        String[] encoded = line.split("\t", -1);
        if (encoded.length != 8) throw new IllegalArgumentException("필드 수 오류");
        String[] f = Arrays.stream(encoded).map(OrganicMechanismTrainer::unb64).toArray(String[]::new);
        List<String> keywords = f[7].isBlank() ? List.of() : Arrays.asList(f[7].split("\u001F", -1));
        return new ReactionItem(f[0], f[1], f[2], f[3], f[4], f[5], f[6], keywords, true);
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static List<String> splitKeywords(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<String> defaultKeywords(String name, String category, String reactants) {
        List<String> result = new ArrayList<>();
        result.add(name);
        result.add(category);
        if (!reactants.isBlank()) {
            Arrays.stream(reactants.split("[+,/]"))
                    .map(String::trim)
                    .filter(s -> s.length() >= 2)
                    .limit(4)
                    .forEach(result::add);
        }
        return result;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", "")
                .replace("에테르", "에터")
                .replace("friedel", "프리델")
                .replace("crafts", "크래프츠");
    }

    private static boolean containsFlexible(String answer, String keyword) {
        String k = normalize(keyword);
        if (k.isBlank()) return true;
        if (answer.contains(k)) return true;
        Map<String, List<String>> synonyms = Map.ofEntries(
                Map.entry("sn1", List.of("sn1", "단분자친핵성치환")),
                Map.entry("sn2", List.of("sn2", "이분자친핵성치환")),
                Map.entry("e1", List.of("e1", "단분자제거")),
                Map.entry("e2", List.of("e2", "이분자제거")),
                Map.entry("카보양이온", List.of("카보양이온", "탄소양이온", "carbocation")),
                Map.entry("친전자체", List.of("친전자체", "electrophile")),
                Map.entry("친핵체", List.of("친핵체", "nucleophile")),
                Map.entry("양성자화", List.of("양성자화", "protonation")),
                Map.entry("탈양성자화", List.of("탈양성자화", "deprotonation"))
        );
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (k.contains(normalize(entry.getKey()))) {
                for (String alt : entry.getValue()) {
                    if (answer.contains(normalize(alt))) return true;
                }
            }
        }
        return false;
    }

    private static List<ReactionItem> createBuiltInItems() {
        List<ReactionItem> x = new ArrayList<>();
        x.add(item("나이트로벤젠", "C6H5NO2", "나이트로화 EAS",
                "벤젠 + 진한 HNO3", "진한 H2SO4, 약 50–60 °C", "나이트로벤젠",
                "H2SO4가 HNO3를 양성자화해 NO2+를 만든다. 벤젠의 π 전자가 NO2+를 공격하여 σ-복합체를 형성하고, 염기가 고리의 H+를 제거하면 방향족성이 회복된다.",
                "NO2+", "친전자체", "σ-복합체", "탈양성자화", "방향족성 회복"));
        x.add(item("벤젠설폰산", "C6H5SO3H", "설폰화 EAS",
                "벤젠 + SO3", "발연 황산 또는 SO3/H2SO4", "벤젠설폰산",
                "SO3 또는 양성자화된 SO3가 친전자체로 작용한다. 벤젠이 이를 공격해 σ-복합체를 만들고 탈양성자화되면서 방향족성이 회복된다. 반응은 가역적이다.",
                "SO3", "친전자체", "σ-복합체", "탈양성자화", "가역"));
        x.add(item("브로모벤젠", "C6H5Br", "할로젠화 EAS",
                "벤젠 + Br2", "FeBr3 촉매", "브로모벤젠",
                "FeBr3가 Br2를 활성화하여 강한 친전자성 브로민 종을 만든다. 벤젠 고리가 공격해 σ-복합체를 형성하고 FeBr4−가 H+를 제거하여 방향족성이 회복되고 촉매가 재생된다.",
                "FeBr3", "Br2", "σ-복합체", "탈양성자화", "촉매 재생"));
        x.add(item("아세토페논", "C6H5COCH3", "프리델–크래프츠 아실화",
                "벤젠 + CH3COCl", "AlCl3, 무수 조건", "아세토페논",
                "CH3COCl과 AlCl3로부터 공명 안정화된 아실륨 이온 CH3CO+가 생긴다. 벤젠이 아실륨 이온을 공격해 σ-복합체를 만들고 탈양성자화 후 가수분해하면 케톤이 얻어진다. 아실륨 이온은 보통 재배열되지 않는다.",
                "AlCl3", "아실륨 이온", "σ-복합체", "가수분해", "재배열 없음"));
        x.add(item("에틸벤젠", "C6H5CH2CH3", "프리델–크래프츠 알킬화",
                "벤젠 + CH3CH2Cl", "AlCl3, 무수 조건", "에틸벤젠",
                "AlCl3가 할로젠화알킬을 활성화하여 강한 친전자성 종을 만든다. 벤젠이 공격해 σ-복합체를 형성하고 탈양성자화되어 알킬벤젠이 된다. 과알킬화 가능성을 고려한다.",
                "AlCl3", "친전자체", "σ-복합체", "탈양성자화", "과알킬화"));
        x.add(item("부테인나이트릴", "CH3CH2CH2CN", "SN2",
                "1-브로모프로페인 + NaCN", "극성 비양성자성 용매, 가열 가능", "부테인나이트릴",
                "CN−가 1차 탄소를 뒤쪽에서 한 단계로 공격하면서 C–Br 결합이 동시에 끊어진다. 속도는 기질과 친핵체 농도 모두에 의존하며 탄소 사슬이 한 개 늘어난다.",
                "CN-", "뒤쪽 공격", "한 단계", "C-Br 결합 절단", "SN2"));
        x.add(item("tert-부탄올", "(CH3)3COH", "SN1",
                "tert-부틸 브로마이드 + H2O", "물, 실온 또는 약한 가열", "tert-부탄올",
                "먼저 C–Br 결합이 이온화되어 3차 카보양이온을 만든다. 물이 친핵체로 공격해 옥소늄 이온을 형성하고, 다른 물 분자가 H+를 제거하여 알코올이 된다.",
                "3차 카보양이온", "이온화", "물의 공격", "옥소늄", "탈양성자화", "SN1"));
        x.add(item("2-뷰텐", "CH3CH=CHCH3", "E2",
                "2-브로모뷰테인 + NaOEt", "EtOH, 가열", "2-뷰텐(주로 trans-2-뷰텐)",
                "강염기가 β-수소를 제거하는 동시에 C=C 결합이 형성되고 Br−가 떠나는 단일 단계 반응이다. H와 이탈기는 anti-periplanar 배열이 유리하며 자이체프 생성물이 주생성물이다.",
                "β-수소", "anti-periplanar", "한 단계", "자이체프", "E2"));
        x.add(item("2-메틸프로펜", "(CH3)2C=CH2", "E1",
                "tert-부탄올", "진한 H2SO4, 가열", "2-메틸프로펜",
                "OH가 양성자화되어 물이라는 좋은 이탈기가 된 뒤 물이 빠져 3차 카보양이온이 형성된다. 염기가 β-수소를 제거하면서 C=C 결합이 생긴다.",
                "OH 양성자화", "물 이탈", "3차 카보양이온", "β-수소 제거", "E1"));
        x.add(item("1-브로모뷰테인", "CH3CH2CH2CH2Br", "알코올 + HX",
                "1-뷰탄올 + HBr", "가열 가능", "1-브로모뷰테인",
                "OH가 먼저 양성자화되어 물로 바뀐다. 1차 카보양이온은 불안정하므로 Br−가 탄소를 뒤쪽에서 공격하면서 물이 동시에 이탈하는 SN2형 경로가 우세하다.",
                "OH 양성자화", "Br-", "뒤쪽 공격", "물 이탈", "SN2"));
        x.add(item("2-브로모프로페인", "(CH3)2CHBr", "알코올 + HX",
                "2-프로판올 + HBr", "산성 조건, 필요 시 가열", "2-브로모프로페인",
                "OH가 양성자화되어 물이 되고, 2차 기질에서는 조건에 따라 이온화 후 Br− 공격의 SN1 성격과 직접 치환의 SN2 성격이 모두 가능하다. 교과서적 설명에서는 물 이탈과 Br−의 치환을 핵심으로 둔다.",
                "OH 양성자화", "물 이탈", "Br-", "치환"));
        x.add(item("다이에틸 에터", "CH3CH2OCH2CH3", "알코올의 산 촉매 에터 형성",
                "에탄올 2분자", "진한 H2SO4, 약 140 °C", "다이에틸 에터 + H2O",
                "한 에탄올의 OH가 양성자화된다. 다른 에탄올의 산소가 1차 탄소를 SN2 방식으로 공격하면서 물이 이탈하고, 마지막 탈양성자화로 에터가 형성된다. 더 높은 온도에서는 알켄 생성이 경쟁한다.",
                "OH 양성자화", "에탄올 공격", "SN2", "물 이탈", "탈양성자화"));
        x.add(item("에텐", "CH2=CH2", "알코올 탈수",
                "에탄올", "진한 H2SO4, 약 170 °C", "에텐 + H2O",
                "OH가 양성자화되어 물이라는 좋은 이탈기로 바뀐다. 1차 카보양이온은 매우 불안정하므로 염기가 β-수소를 제거하는 것과 물 이탈이 협동적으로 일어나는 E2형 탈수가 적절하다.",
                "OH 양성자화", "β-수소", "물 이탈", "E2형", "알켄"));
        x.add(item("에틸 에타노에이트", "CH3COOCH2CH3", "피셔 에스터화",
                "에탄올 + 에탄산", "촉매량 H2SO4, 가열, 물 제거", "에틸 에타노에이트 + H2O",
                "카보닐 산소가 양성자화되어 탄소의 친전자성이 증가한다. 에탄올이 친핵성 첨가하여 사면체 중간체를 만들고, 양성자 이동 후 물이 이탈한다. 탈양성자화로 에스터와 산 촉매가 재생된다.",
                "카보닐 양성자화", "친핵성 첨가", "사면체 중간체", "물 이탈", "촉매 재생"));
        x.add(item("메틸 벤조에이트", "C6H5COOCH3", "피셔 에스터화",
                "메탄올 + 벤조산", "촉매량 H2SO4, 환류, 물 제거", "메틸 벤조에이트 + H2O",
                "카보닐 산소 양성자화, 메탄올의 친핵성 첨가, 사면체 중간체 내 양성자 이동, 물 이탈, 탈양성자화 순으로 진행된다.",
                "카보닐 양성자화", "메탄올", "사면체 중간체", "물 이탈", "탈양성자화"));
        x.add(item("1-프로판올", "CH3CH2CH2OH", "알데하이드·케톤 수소화",
                "프로판알 + H2", "Ni, Pt 또는 Pd 촉매", "1-프로판올",
                "금속 표면에 H2와 카보닐 화합물이 흡착된다. 수소가 카보닐 탄소와 산소에 순차적으로 전달되어 C=O가 C–OH로 환원되고 생성물이 표면에서 탈착한다.",
                "H2", "금속 촉매", "C=O 환원", "1차 알코올"));
        x.add(item("2-프로판올", "(CH3)2CHOH", "알데하이드·케톤 수소화",
                "아세톤 + H2", "Ni, Pt 또는 Pd 촉매", "2-프로판올",
                "금속 촉매 표면에서 H2가 활성화되고 카보닐의 π 결합에 수소가 첨가된다. 케톤은 2차 알코올로 환원된다.",
                "H2", "금속 촉매", "카보닐 환원", "2차 알코올"));
        x.add(item("벤질 알코올", "C6H5CH2OH", "카복실산·에스터 환원",
                "벤조산", "1) LiAlH4, 무수 에터  2) H3O+", "벤질 알코올",
                "LiAlH4의 H−가 카복실산 유도체의 탄소에 전달된다. 여러 차례의 수소화물 전달과 알루미늄 착물 형성 후 산성 처리에서 1차 알코올이 방출된다.",
                "LiAlH4", "무수 에터", "수소화물 전달", "산성 처리", "1차 알코올"));
        x.add(item("1-뷰탄올", "CH3CH2CH2CH2OH", "카복실산·에스터 환원",
                "에틸 뷰타노에이트", "1) 과량 LiAlH4, 무수 에터  2) H3O+", "1-뷰탄올 + 에탄올",
                "수소화물이 에스터 카보닐을 공격해 사면체 중간체를 만들고 알콕사이드가 이탈하여 알데하이드가 된다. 두 번째 수소화물 공격으로 1차 알콕사이드가 되고 산성 처리에서 알코올이 된다. 이탈한 에톡시기도 에탄올로 전환된다.",
                "LiAlH4", "사면체 중간체", "알콕사이드 이탈", "알데하이드", "두 번째 수소화물", "산성 처리"));
        x.add(item("2-메틸-2-프로판올", "(CH3)3COH", "그리냐르 반응",
                "아세톤 + CH3MgBr", "1) 무수 에터  2) H3O+", "2-메틸-2-프로판올",
                "CH3MgBr의 탄소가 친핵체처럼 카보닐 탄소를 공격하여 C–C 결합과 3차 알콕사이드 마그네슘 염을 만든다. 산성 처리에서 알콕사이드가 양성자화되어 3차 알코올이 된다.",
                "CH3MgBr", "무수 에터", "카보닐 공격", "C-C 결합", "알콕사이드", "산성 처리"));
        x.add(item("2-뷰탄올", "CH3CH(OH)CH2CH3", "그리냐르 반응",
                "에탄알 + CH3CH2MgBr", "1) 무수 에터  2) H3O+", "2-뷰탄올",
                "에틸 그리냐르 시약의 탄소가 에탄알의 카보닐 탄소를 공격해 새 C–C 결합과 2차 알콕사이드를 만든다. 산성 처리에서 2차 알코올이 된다.",
                "그리냐르", "무수 에터", "카보닐 공격", "C-C 결합", "2차 알콕사이드", "산성 처리"));
        x.add(item("트라이페닐메탄올", "(C6H5)3COH", "그리냐르 반응",
                "벤조페논 + C6H5MgBr", "1) 무수 에터  2) H3O+", "트라이페닐메탄올",
                "페닐 그리냐르 시약이 벤조페논의 카보닐 탄소를 친핵성 공격하여 세 번째 페닐–탄소 결합과 3차 알콕사이드를 만든다. 산성 처리로 트라이페닐메탄올을 얻는다.",
                "C6H5MgBr", "카보닐 공격", "C-C 결합", "3차 알콕사이드", "산성 처리"));
        x.add(item("프로판알", "CH3CH2CHO", "알코올 산화",
                "1-프로판올", "PCC, CH2Cl2, 무수 조건", "프로판알",
                "알코올 산소가 Cr(VI)에 결합해 크로메이트 에스터를 만든다. 염기가 α-수소를 제거하면서 C=O가 형성되고 Cr–O 결합이 끊어진다. 무수 조건이라 알데하이드의 과산화가 억제된다.",
                "PCC", "크로메이트 에스터", "α-수소 제거", "C=O", "과산화 억제"));
        x.add(item("프로판산", "CH3CH2COOH", "알코올 산화",
                "1-프로판올", "K2Cr2O7/H2SO4 또는 CrO3/H2SO4, 가열", "프로판산",
                "1차 알코올이 먼저 알데하이드로 산화되고, 수용액에서 알데하이드가 수화된 뒤 다시 산화되어 카복실산이 된다. 강한 산화제와 물이 존재하므로 산화가 알데하이드에서 멈추지 않는다.",
                "강한 산화제", "알데하이드", "수화", "추가 산화", "카복실산"));
        x.add(item("아세톤", "(CH3)2CO", "알코올 산화",
                "2-프로판올", "PCC 또는 K2Cr2O7/H2SO4", "아세톤",
                "2차 알코올이 크로메이트 에스터를 형성한 뒤 α-수소 제거와 함께 C=O가 생성되어 케톤이 된다. 케톤에는 같은 방식으로 제거할 카보닐 탄소의 수소가 없어 보통 더 산화되지 않는다.",
                "2차 알코올", "크로메이트 에스터", "α-수소 제거", "케톤"));
        x.add(item("브로모에테인", "CH3CH2Br", "에터 산 촉매 분해",
                "다이에틸 에터 + 과량 HBr", "가열", "브로모에테인 2분자 + H2O",
                "에터 산소가 양성자화된다. Br−가 1차 에틸 탄소를 SN2 방식으로 공격해 한 분자의 브로모에테인과 에탄올을 만든다. 과량 HBr에서는 에탄올도 양성자화 후 SN2 치환되어 두 번째 브로모에테인이 된다.",
                "에터 산소 양성자화", "Br-", "SN2", "C-O 결합 절단", "과량 HBr"));
        x.add(item("tert-부틸 브로마이드", "(CH3)3CBr", "에터 산 촉매 분해",
                "메틸 tert-부틸 에터 + HBr", "산성 조건", "tert-부틸 브로마이드 + 메탄올",
                "에터 산소가 양성자화된 뒤 3차 탄소–산소 결합이 끊어져 안정한 tert-부틸 카보양이온이 형성된다. Br−가 이를 포착하여 tert-부틸 브로마이드가 되고 메탄올이 함께 생성된다. 3차 쪽은 SN1 경로가 유리하다.",
                "에터 산소 양성자화", "3차 카보양이온", "C-O 결합 절단", "Br- 포착", "SN1"));
        x.add(item("2-메톡시에탄올", "CH3OCH2CH2OH", "에폭사이드 염기성 고리 열림",
                "에틸렌 옥사이드 + CH3O−", "CH3OH 용매 후 양성자화", "2-메톡시에탄올",
                "강한 친핵체 CH3O−가 에폭사이드 탄소를 뒤쪽에서 SN2 방식으로 공격한다. C–O 결합 하나가 끊어져 알콕사이드가 되고, 메탄올로부터 H+를 받아 알코올이 된다.",
                "CH3O-", "뒤쪽 공격", "SN2", "고리 열림", "알콕사이드", "양성자화"));
        x.add(item("1-메톡시프로판-2-올", "CH3OCH2CH(OH)CH3", "에폭사이드 염기성 고리 열림",
                "프로필렌 옥사이드 + CH3O−", "CH3OH 용매 후 양성자화", "1-메톡시프로판-2-올",
                "염기성 조건에서는 CH3O−가 입체장애가 작은 덜 치환된 에폭사이드 탄소를 SN2 방식으로 공격한다. 고리가 열려 더 치환된 탄소 쪽 산소가 알콕사이드가 되고 양성자화되어 OH가 된다.",
                "덜 치환된 탄소", "CH3O-", "SN2", "고리 열림", "알콕사이드", "양성자화"));
        x.add(item("2-메톡시프로판-1-올", "CH3CH(OCH3)CH2OH", "에폭사이드 산성 고리 열림",
                "프로필렌 옥사이드 + CH3OH", "촉매량 H+", "2-메톡시프로판-1-올",
                "에폭사이드 산소가 먼저 양성자화되어 고리가 활성화된다. 메탄올은 부분적인 카보양이온 성격이 더 큰 더 치환된 탄소를 공격한다. 고리 열림 후 탈양성자화되어 메톡시 알코올이 된다.",
                "에폭사이드 양성자화", "더 치환된 탄소", "메탄올 공격", "고리 열림", "탈양성자화"));

        if (x.size() != 30) throw new IllegalStateException("기본 문제은행은 정확히 30개여야 합니다: " + x.size());
        return Collections.unmodifiableList(x);
    }


    private static ReactionItem item(String name, String formula, String category, String reactants,
                                     String conditions, String product, String mechanism, String... keywords) {
        List<String> list = new ArrayList<>(Arrays.asList(keywords));
        list.add(category);
        return new ReactionItem(name, formula, category, reactants, conditions, product, mechanism, list, false);
    }

    private enum QuizMode {
        SYNTHESIS("목표 생성물 → 합성 설계"),
        PREDICTION("반응물·조건 → 생성물 예측"),
        RANDOM("완전 랜덤");

        private final String label;
        QuizMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private record ReactionItem(
            String name,
            String formula,
            String category,
            String reactants,
            String conditions,
            String product,
            String mechanism,
            List<String> keywords,
            boolean custom
    ) {
        boolean canPredict() {
            return reactants != null && !reactants.isBlank() && mechanism != null && !mechanism.isBlank();
        }
    }

    private class SyllabusTableModel extends AbstractTableModel {
        private final String[] columns = {"번호", "화합물", "분자식/축약식", "반응 유형", "구분"};
        @Override public int getRowCount() { return allItems().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ReactionItem item = allItems().get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> item.name;
                case 2 -> item.formula;
                case 3 -> item.category;
                case 4 -> item.custom ? "사용자" : "기본";
                default -> "";
            };
        }
        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }
    }

    private class CustomTableModel extends AbstractTableModel {
        private final String[] columns = {"번호", "화합물", "반응 유형", "반응물"};
        @Override public int getRowCount() { return customItems.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ReactionItem item = customItems.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> item.name;
                case 2 -> item.category;
                case 3 -> item.reactants;
                default -> "";
            };
        }
        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }
    }

    private static final class GothicHallPanel extends JPanel {
        GothicHallPanel() { setOpaque(true); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(13, 7, 24), 0, h, new Color(46, 23, 73)));
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(205, 209, 220, 28));
            for (int y = 110; y < h; y += 44) g2.drawLine(0, y, w, y);
            for (int x = 0; x < w; x += 92) g2.drawLine(x, 110, x - 150, h);

            RadialGradientPaint glow = new RadialGradientPaint(
                    new Point(w / 2, h / 3), Math.max(w, h) * 0.55f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(104, 70, 142, 90), new Color(10, 5, 18, 0)});
            g2.setPaint(glow);
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(220, 225, 238, 150));
            int[][] stars = {{70,80},{155,145},{w-110,105},{w-220,175},{w/2,75},{w/2-270,210},{w/2+300,230}};
            for (int[] p : stars) {
                g2.fillOval(p[0], p[1], 3, 3);
                g2.drawLine(p[0]-4, p[1]+1, p[0]+7, p[1]+1);
                g2.drawLine(p[0]+1, p[1]-4, p[0]+1, p[1]+7);
            }
            g2.dispose();
        }
    }

    private static final class GothicDoorButton extends JButton {
        private final String upper;
        private final String main;
        private final String description;

        GothicDoorButton(String upper, String main, String description) {
            this.upper = upper;
            this.main = main;
            this.description = description;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(270, 455));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { repaint(); }
                @Override public void mouseExited(MouseEvent e) { repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int x = 13;
            int y = 10;
            int doorW = w - 26;
            int bottom = h - 13;
            int shoulder = y + 105;
            int center = w / 2;

            Path2D door = new Path2D.Double();
            door.moveTo(x, bottom);
            door.lineTo(x, shoulder);
            door.quadTo(x + 8, y + 26, center, y);
            door.quadTo(x + doorW - 8, y + 26, x + doorW, shoulder);
            door.lineTo(x + doorW, bottom);
            door.closePath();

            boolean hover = getModel().isRollover();
            g2.setColor(new Color(0, 0, 0, 105));
            g2.translate(7, 8);
            g2.fill(door);
            g2.translate(-7, -8);

            g2.setPaint(new GradientPaint(0, y, hover ? new Color(89, 53, 130) : new Color(55, 28, 86),
                    0, bottom, new Color(23, 12, 39)));
            g2.fill(door);
            g2.setStroke(new BasicStroke(hover ? 4.2f : 3.1f));
            g2.setColor(hover ? new Color(236, 239, 247) : SILVER);
            g2.draw(door);

            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(205, 209, 220, 100));
            g2.drawLine(x + 22, shoulder + 10, x + 22, bottom - 18);
            g2.drawLine(x + doorW - 22, shoulder + 10, x + doorW - 22, bottom - 18);
            g2.drawArc(x + 22, y + 20, doorW - 44, 68, 0, 180);
            g2.drawOval(center - 37, shoulder + 18, 74, 74);
            g2.drawLine(center, shoulder + 18, center, shoulder + 92);
            g2.drawLine(center - 37, shoulder + 55, center + 37, shoulder + 55);

            g2.setColor(new Color(225, 229, 239));
            g2.setFont(new Font(Font.SERIF, Font.BOLD, 13));
            drawCentered(g2, upper, center, shoulder + 135);

            g2.setFont(new Font(Font.SERIF, Font.BOLD, hover ? 24 : 22));
            g2.setColor(Color.WHITE);
            drawCentered(g2, main, center, shoulder + 180);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g2.setColor(new Color(202, 193, 214));
            int lineY = shoulder + 218;
            for (String line : description.split("\\n")) {
                drawCentered(g2, line, center, lineY);
                lineY += 21;
            }

            g2.setColor(hover ? Color.WHITE : SILVER);
            g2.fillOval(x + doorW - 37, h / 2 + 42, 9, 9);
            if (hover) {
                g2.setColor(new Color(240, 241, 248, 100));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x + doorW - 44, h / 2 + 35, 23, 23);
            }
            g2.dispose();
        }

        private static void drawCentered(Graphics2D g2, String text, int centerX, int baselineY) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
        }
    }

    private static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        UIManager.put("OptionPane.background", PARCHMENT);
        UIManager.put("Panel.background", PARCHMENT);
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--self-test")) {
            List<ReactionItem> items = createBuiltInItems();
            System.out.println("Built-in items: " + items.size());
            System.out.println("Categories: " + items.stream().map(ReactionItem::category).distinct().count());
            System.out.println("Predictable: " + items.stream().filter(ReactionItem::canPredict).count());
            System.out.println("Self-test passed.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            setLookAndFeel();
            new OrganicMechanismTrainer().setVisible(true);
        });
    }
}
