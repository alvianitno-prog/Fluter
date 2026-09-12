const TelegramBot = require('node-telegram-bot-api');
const fs = require('fs');

// Ganti dengan token bot kamu dari @BotFather
const token = '8453097342:AAECvXCqNeJyzn9OEatFJpwPiKT5gBIynA0';
const bot = new TelegramBot(token, { polling: true });

// --- KONFIGURASI UTAMA ---
const BOT_USERNAME = '@BotJaser_bot'; 
const OWNER_ID = '7625804862';
const OWNER_USERNAME = 'SamuDev'; 
const PHOTO_URL = 'https://files.catbox.moe/8m54cw.jpg'; 
const REQUIRED_CHANNEL = '@samudev1'; // Channel wajib join

// --- DATABASE BERBASIS FILE JSON ---
const GROUPS_FILE = './groups.json';
const PREMIUM_FILE = './premium.json';
const USERS_FILE = './users.json';

// Penyimpanan Sesi Auto Share Aktif per User (Untuk Fitur /stop)
const activeAutoShares = new Map();

// Escape HTML agar nama/username yang mengandung < > & tidak merusak parse_mode
function escapeHTML(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

// Fungsi Aman untuk Membaca File JSON
function loadJsonFile(filePath) {
    if (fs.existsSync(filePath)) {
        try {
            const data = fs.readFileSync(filePath, 'utf8');
            const parsed = JSON.parse(data);
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }
    return [];
}

// Fungsi Aman untuk Menyimpan File JSON
function saveJsonFile(filePath, data) {
    try {
        fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
    } catch (e) {
        console.error(`Gagal menyimpan file ${filePath}:`, e);
    }
}

// Helper Pengecekan Admin / Owner
function checkIsAdmin(userId, username) {
    const stringId = userId.toString();
    return stringId === OWNER_ID || username === OWNER_USERNAME;
}

// Helper Cek Status Premium
function checkIsPremium(userId, username) {
    const premiumUsers = loadJsonFile(PREMIUM_FILE);
    const stringId = userId.toString();
    
    if (checkIsAdmin(userId, username)) return true;
    const isExist = premiumUsers.some(item => (typeof item === 'string' ? item === stringId : item.id === stringId));
    if (isExist) return true;
    
    return false;
}

// Fungsi Pengecekan Wajib Join Channel
async function checkSubscription(userId) {
    try {
        const chatMember = await bot.getChatMember(REQUIRED_CHANNEL, userId);
        const status = chatMember.status;
        if (['creator', 'administrator', 'member'].includes(status)) {
            return true;
        }
        return false;
    } catch (err) {
        return false;
    }
}

// --- TATA LETAK TOMBOL INLINE UTAMA ---
function getMainMenu(userId, username) {
    let keyboard = [
        [{ text: '𝑱𝒂𝒔𝒉𝒂𝒓𝒆 𝑴𝒆𝒏𝒖', callback_data: 'menu_jashare', style: "danger" }],
        [
            { text: '𝑪𝒆𝒌 𝑰𝑫', callback_data: 'menu_cekid', style: "primary" },
            { text: '𝑩𝒖𝒚 𝑨𝒄𝒄𝒆𝒔𝒔', callback_data: 'menu_buy', style: "success" }
        ],
        [{ text: '𝑺𝒚𝒂𝒓𝒂𝒕 𝑨𝒌𝒔𝒆𝒔 𝑭𝒓𝒆𝒆', callback_data: 'menu_free', style: "danger" }],
        [{ text: '➕ 𝑴𝒂𝒔𝒖𝒌𝒌𝒂𝒏 𝑩𝒐𝒕 𝑲𝒆 𝑮𝒓𝒐𝒖𝒑', url: `https://t.me/${BOT_USERNAME}?startgroup=true`, style: "success" }]
    ];

    if (checkIsAdmin(userId, username)) {
        keyboard.push([{ text: '𝑴𝒆𝒏𝒖 𝑨𝒅𝒎𝒊𝒏 𝑩𝒐𝒕', callback_data: 'menu_admin', style: "primary" }]);
    }

    return { reply_markup: { inline_keyboard: keyboard } };
}

const adminMenuKeyboard = {
    reply_markup: {
        inline_keyboard: [
            [
                { text: '📋 List Group', callback_data: 'admin_listgroup_1', style: "primary" },
                { text: '💎 List Premium', callback_data: 'admin_listprem_1', style: "primary" }
            ],
            [
                { text: '➕ Add Prem', callback_data: 'admin_howto_add', style: "danger" },
                { text: '➖ Del Prem', callback_data: 'admin_howto_del', style: "danger" }
            ],
            [
                { text: '📢 Broadcast Owner', callback_data: 'admin_howto_bc', style: "success" },
                { text: '📦 Backup DB', callback_data: 'admin_backup', style: "success" }
            ],
            [{ text: '🔙 𝑩𝒂𝒄𝒌', callback_data: 'menu_back', style: "primary" }]
        ]
    }
};

const freeAccessKeyboard = {
    reply_markup: {
        inline_keyboard: [
            [{ text: '➕ 𝑴𝒂𝒔𝒖𝒌𝒌𝒂𝒏 𝑩𝒐𝒕 𝑲𝒆 𝑮𝒓𝒐𝒖𝒑', url: `https://t.me/${BOT_USERNAME}?startgroup=true`, style: "success" }],
            [{ text: '🔄 𝑽𝒆𝒓𝒊𝒇𝒊𝒌𝒂𝒔𝒊 / 𝑪𝒆𝒌 𝑺𝒕𝒂𝒕𝒖𝒔', callback_data: 'check_group_status', style: "primary" }],
            [{ text: '🔙 𝑩𝒂𝒄𝒌 ', callback_data: 'menu_back', style: "danger" }]
        ]
    }
};

const backKeyboard = {
    reply_markup: {
        inline_keyboard: [
            [{ text: '🔙 𝑩𝒂𝒄𝒌 ', callback_data: 'menu_back', style: "primary" }]
        ]
    }
};

const backAdminKeyboard = {
    reply_markup: {
        inline_keyboard: [
            [{ text: '🔙 𝑩𝒂𝒄𝒌 𝑻𝒐 𝑨𝒅𝒎𝒊𝒏 𝑷𝒂𝒏𝒆𝒍', callback_data: 'menu_admin', style: "success" }]
        ]
    }
};


// ==========================================
// --- DETEKSI BOT MASUK/KELUAR GROUP & USERS ---
// ==========================================
bot.on('my_chat_member', async (msg) => {
    const chat = msg.chat;
    const oldStatus = msg.old_chat_member ? msg.old_chat_member.status : '';
    const newStatus = msg.new_chat_member ? msg.new_chat_member.status : '';
    const user = msg.from; // Orang yang memasukkan atau mengeluarkan bot

    if (chat.type === 'group' || chat.type === 'supergroup') {
        const chatId = chat.id.toString();
        let chatTitle = chat.title || 'Unknown Group';
        let chatUsername = chat.username ? `@${chat.username}` : null;

        const isBotJoined = (['left', 'kicked', 'restricted', ''].includes(oldStatus)) && (['member', 'administrator'].includes(newStatus));
        const isBotLeft = (['member', 'administrator'].includes(oldStatus)) && (['left', 'kicked'].includes(newStatus));

        // 1. KONDISI BOT DIMASUKKAN KE GROUP
        if (isBotJoined) {
            try {
                const chatInfo = await bot.getChat(chatId);
                if (chatInfo.title) chatTitle = chatInfo.title;
                if (chatInfo.username) chatUsername = `@${chatInfo.username}`;
            } catch (e) {}

            if (!chatUsername) {
                try {
                    const inviteLink = await bot.exportChatInviteLink(chatId);
                    chatUsername = inviteLink;
                } catch (err) {
                    chatUsername = null;
                }
            }

            let registeredGroups = loadJsonFile(GROUPS_FILE);
            let existingIndex = registeredGroups.findIndex(g => (typeof g === 'string' ? g === chatId : g.id === chatId));
            
            const groupData = { 
                id: chatId, 
                title: chatTitle, 
                username: chatUsername, 
                inviter_id: user ? user.id.toString() : null 
            };

            if (existingIndex !== -1) {
                registeredGroups[existingIndex] = groupData;
            } else {
                registeredGroups.push(groupData);
                
                let inviterName = user && user.first_name ? escapeHTML(user.first_name) : 'Seseorang';
                let inviterUsn = user && user.username ? `@${escapeHTML(user.username)}` : null;

                let formattedInviter = inviterName;
                if (inviterUsn) {
                    formattedInviter = `<a href="https://t.me/${inviterUsn.replace('@', '')}">${inviterName} (${inviterUsn})</a>`;
                }

                let formattedGroupLink = escapeHTML(chatTitle);
                if (chatUsername) {
                    if (chatUsername.startsWith('http')) {
                        formattedGroupLink = `<a href="${chatUsername}">${escapeHTML(chatTitle)}</a>`;
                    } else if (chatUsername.startsWith('@')) {
                        formattedGroupLink = `<a href="https://t.me/${chatUsername.replace('@', '')}">${escapeHTML(chatTitle)}</a>`;
                    }
                }

                const logText = 
                    `<blockquote>📊 <b>[ SYSTEM NOTIFICATION ]</b>\n` +
                    `───────────────────────\n` +
                    `📌 <b>NEW GROUP REGISTERED</b>\n\n` +
                    `• Nama Group : ${formattedGroupLink}\n` +
                    `• Group ID   : ${chatId}\n` +
                    `• Diundang   : ${formattedInviter}\n` +
                    `• Total Group: ${registeredGroups.length}</blockquote>`;
                
                bot.sendMessage(REQUIRED_CHANNEL, logText, { parse_mode: 'HTML', disable_web_page_preview: true }).catch(() => {});
            }
            saveJsonFile(GROUPS_FILE, registeredGroups);

            const welcomeGroupText = 
                `<blockquote>✨ <b>[ WELCOME NOTIFICATION ]</b>\n` +
                `───────────────────────\n` +
                `<b>Terima kasih telah mengundangku ke group ini!</b>\n\n` +
                `🤖 Bot Jashare siap digunakan.\n` +
                `📌 Silakan kembali ke Private Chat bot dan klik <b>Verifikasi / Cek Status</b> untuk mendapatkan akses Premium!</blockquote>`;

            bot.sendMessage(chatId, welcomeGroupText, { parse_mode: 'HTML' }).catch(() => {});
        }

        // 2. KONDISI BOT DIKELUARKAN DARI GROUP (AUTO UN-PREM)
        else if (isBotLeft) {
            let registeredGroups = loadJsonFile(GROUPS_FILE);
            
            // Cari data group yang dihapus untuk mengetahui siapa inviter-nya
            const removedGroup = registeredGroups.find(g => (typeof g === 'string' ? g === chatId : g.id === chatId));
            const groupTitle = removedGroup && typeof removedGroup === 'object' ? removedGroup.title : chatTitle;
            let groupUsn = (removedGroup && typeof removedGroup === 'object' && removedGroup.username) ? removedGroup.username : (chatUsername || null);
            const inviterId = removedGroup && typeof removedGroup === 'object' ? removedGroup.inviter_id : null;

            let updatedGroups = registeredGroups.filter(g => {
                const gId = typeof g === 'string' ? g : g.id;
                return gId !== chatId;
            });

            saveJsonFile(GROUPS_FILE, updatedGroups);

            // AUTO UN-PREM: Jika user yang mengundang bot dulu mengeluarkan bot, cek apakah dia masih punya grup lain.
            // Jika tidak punya grup lain yang aktif, hapus status premiumnya.
            if (inviterId) {
                let stillHasOtherGroup = updatedGroups.some(g => typeof g === 'object' && g.inviter_id === inviterId);
                
                if (!stillHasOtherGroup) {
                    let premiumUsers = loadJsonFile(PREMIUM_FILE);
                    let updatedPrems = premiumUsers.filter(item => {
                        const idVal = typeof item === 'string' ? item : item.id;
                        return idVal !== inviterId;
                    });
                    
                    if (updatedPrems.length < premiumUsers.length) {
                        saveJsonFile(PREMIUM_FILE, updatedPrems);
                        
                        // Kirim notifikasi ke user bahwa akses premiumnya dicabut karena bot dikeluarkan dari grup
                        const unpermNotice = 
                            `<blockquote>⚠️ <b>[ PREMIUM REVOKED ]</b>\n` +
                            `───────────────────────\n` +
                            `<b>Akses Premium Kamu Dicabut!</b>\n\n` +
                            `Karena bot dikeluarkan dari group <b>${escapeHTML(groupTitle)}</b>, status Premium otomatis kedaluwarsa/dicabut. Masukkan kembali bot ke group untuk mengaktifkannya lagi.</blockquote>`;
                        bot.sendMessage(inviterId, unpermNotice, { parse_mode: 'HTML' }).catch(() => {});
                    }
                }
            }

            let kickerName = user && user.first_name ? escapeHTML(user.first_name) : 'Seseorang';
            let kickerUsn = user && user.username ? `@${escapeHTML(user.username)}` : null;

            if (!user || user.username === 'GroupAnonymousBot' || user.id === 1087968824) {
                kickerName = `Admin / Sistem Group`;
                kickerUsn = null;
            }

            let formattedKicker = kickerName;
            if (kickerUsn) {
                formattedKicker = `<a href="https://t.me/${kickerUsn.replace('@', '')}">${kickerName} (${kickerUsn})</a>`;
            }

            let formattedGroupLink = escapeHTML(groupTitle);
            if (groupUsn) {
                if (groupUsn.startsWith('http')) {
                    formattedGroupLink = `<a href="${groupUsn}">${escapeHTML(groupTitle)}</a>`;
                } else if (groupUsn.startsWith('@')) {
                    formattedGroupLink = `<a href="https://t.me/${groupUsn.replace('@', '')}">${escapeHTML(groupTitle)}</a>`;
                }
            }

            const logKickText = 
                `<blockquote>⚠️ <b>[ SYSTEM NOTIFICATION ]</b>\n` +
                `───────────────────────\n` +
                `❌ <b>BOT REMOVED FROM GROUP (UN-PREM)</b>\n\n` +
                `• Nama Group : ${formattedGroupLink}\n` +
                `• Group ID   : ${chatId}\n` +
                `• Dikeluarkan oleh: ${formattedKicker}\n` +
                `• Sisa Group : ${updatedGroups.length} Active Groups</blockquote>`;

            bot.sendMessage(REQUIRED_CHANNEL, logKickText, { parse_mode: 'HTML', disable_web_page_preview: true }).catch(() => {});
        }
    }
});

bot.on('message', async (msg) => {
    const userId = msg.from ? msg.from.id.toString() : null;
    const user = msg.from;

    if (userId && !user.is_bot && msg.chat.type === 'private') {
        let registeredUsers = loadJsonFile(USERS_FILE);
        let userStrList = registeredUsers.map(u => (typeof u === 'string' ? u : u.id));

        if (!userStrList.includes(userId)) {
            registeredUsers.push({ id: userId, name: user.first_name, username: user.username || '-' });
            saveJsonFile(USERS_FILE, registeredUsers);

            let formattedUser = `${escapeHTML(user.first_name)}`;
            if (user.username) {
                formattedUser = `<a href="https://t.me/${user.username}">${escapeHTML(user.first_name)} (@${escapeHTML(user.username)})</a>`;
            }

            const logNewUser = 
                `<blockquote>👤 <b>[ NEW USER BOT ]</b>\n` +
                `───────────────────────\n` +
                `👤 <b>NEW USER BOT DETECTED</b>\n\n` +
                `• Nama    : ${formattedUser}\n` +
                `• ID      : ${userId}\n` +
                `• Total   : ${registeredUsers.length} Users</blockquote>`;

            bot.sendMessage(REQUIRED_CHANNEL, logNewUser, { parse_mode: 'HTML', disable_web_page_preview: true }).catch(() => {});
        }
    }
});


// ==========================================
// --- COMMAND /START & TAMPILAN UTAMA ---
// ==========================================
bot.onText(/\/start/, async (msg) => {
    if (msg.chat.type !== 'private') return;

    const chatId = msg.chat.id;
    const userId = msg.from.id;
    const username = msg.from.username;

    const isJoined = await checkSubscription(userId);

    if (!isJoined) {
        const joinText = 
            `<blockquote>❌ <b>[ ACCESS CONTROL ]</b>\n` +
            `───────────────────────\n` +
            `<b>AKSES DITOLAK!</b>\n\n` +
            `Halo Kak! Agar bisa menggunakan bot ini, kamu wajib bergabung ke channel resmi kami terlebih dahulu di ${REQUIRED_CHANNEL}.\n\n` +
            `📌 <b>Langkah-langkah:</b>\n` +
            `1️⃣ Klik tombol <b>"Join Channel"</b> di bawah.\n` +
            `2️⃣ Bergabunglah ke dalam channel.\n` +
            `3️⃣ Kembali ke sini lalu klik <b>"Sudah Join"</b>.</blockquote>`;

        const joinKeyboard = {
            reply_markup: {
                inline_keyboard: [
                    [{ text: '📢 Join Channel', url: `https://t.me/${REQUIRED_CHANNEL.replace('@', '')}` }],
                    [{ text: '✅ Sudah Join', callback_data: 'check_sub_status' }]
                ]
            }
        };

        return bot.sendMessage(chatId, joinText, { parse_mode: 'HTML', ...joinKeyboard });
    }

    sendMainMenuDisplay(chatId, userId, username);
});

function sendMainMenuDisplay(chatId, userId, username, messageId = null) {
    const menuMarkup = getMainMenu(userId, username);
    const registeredGroups = loadJsonFile(GROUPS_FILE);
    const registeredUsers = loadJsonFile(USERS_FILE);

    const text = 
        `<blockquote>📊 <b>[ SYSTEM RUNNING ]</b>\n` +
        `───────────────────────\n` +
        `👤 Developer : @${OWNER_USERNAME}\n` +
        `🤖 Nama Bot  : Bot jasher iwaw\n` +
        `⚡ Prefix    : /\n` +
        `👥 Group     : ${registeredGroups.length}\n` +
        `👤 Users     : ${registeredUsers.length}\n` +
        `⏳ Uptime    : Aktif Berjalan</blockquote>\n\n` +
        `<blockquote>⚙️ <b>[ DASHBOARD INFO ]</b>\n` +
        `───────────────────────\n` +
        `• Statistik group dioptimalkan untuk publik.\n` +
        `• Bot berjalan stabil & dicek secara rutin.\n` +
        `• Enjoy fitur yang ada ✨</blockquote>\n\n` +
        `<blockquote>💻 <b>[ DEVELOPER SCRIPT ]</b>\n` +
        `───────────────────────\n` +
        `<i>Dikembangin oleh ${OWNER_USERNAME.toUpperCase()}</i></blockquote>`;

    if (messageId) {
        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: menuMarkup.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {
            bot.sendMessage(chatId, text, { ...menuMarkup, parse_mode: 'HTML' });
        });
    } else {
        bot.sendPhoto(chatId, PHOTO_URL, {
            caption: text,
            reply_markup: menuMarkup.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {
            bot.sendMessage(chatId, text, { ...menuMarkup, parse_mode: 'HTML' });
        });
    }
}


// ==========================================
// --- COMMAND ADMIN ---
// ==========================================
bot.onText(/\/addprem(?: (.+))?/, (msg, match) => {
    if (msg.chat.type !== 'private') return;
    const userId = msg.from.id;
    const username = msg.from.username;

    if (!checkIsAdmin(userId, username)) {
        return bot.sendMessage(msg.chat.id, "<blockquote>🔒 <b>[ SECURITY ALERT ]</b>\n\n❌ Perintah ini khusus untuk Owner bot!</blockquote>", { parse_mode: 'HTML' });
    }

    const targetId = match[1] ? match[1].trim() : null;
    if (!targetId) {
        return bot.sendMessage(msg.chat.id, "<blockquote>⚠️ <b>[ FORMAT ERROR ]</b>\n\nGunakan format:\n/addprem ID_USER</blockquote>", { parse_mode: 'HTML' });
    }

    let premiumUsers = loadJsonFile(PREMIUM_FILE);
    let premListStr = premiumUsers.map(p => (typeof p === 'string' ? p : p.id));
    if (premListStr.includes(targetId)) {
        return bot.sendMessage(msg.chat.id, `<blockquote>⚠️ <b>[ DATABASE NOTICE ]</b>\n\nUser dengan ID ${targetId} sudah terdaftar sebagai Premium.</blockquote>`, { parse_mode: 'HTML' });
    }

    premiumUsers.push(targetId);
    saveJsonFile(PREMIUM_FILE, premiumUsers);

    const logManualPrem = 
        `<blockquote>💎 <b>[ PREMIUM METRICS ]</b>\n` +
        `───────────────────────\n` +
        `💎 <b>NEW PREMIUM USER (MANUAL)</b>\n\n` +
        `• Target ID : ${targetId}\n` +
        `• Status    : Diberi Akses Premium oleh Admin (${username || OWNER_USERNAME})</blockquote>`;
    bot.sendMessage(REQUIRED_CHANNEL, logManualPrem, { parse_mode: 'HTML' }).catch(() => {});

    bot.sendMessage(msg.chat.id, `<blockquote>✅ <b>[ OPERATION SUCCESS ]</b>\n\n<b>Berhasil!</b>\nUser ID ${targetId} sekarang memiliki akses Premium.</blockquote>`, { parse_mode: 'HTML' });
});

bot.onText(/\/delprem(?: (.+))?/, (msg, match) => {
    if (msg.chat.type !== 'private') return;
    const userId = msg.from.id;
    const username = msg.from.username;

    if (!checkIsAdmin(userId, username)) {
        return bot.sendMessage(msg.chat.id, "<blockquote>🔒 <b>[ SECURITY ALERT ]</b>\n\n❌ Perintah ini khusus untuk Owner bot!</blockquote>", { parse_mode: 'HTML' });
    }

    const targetId = match[1] ? match[1].trim() : null;
    if (!targetId) {
        return bot.sendMessage(msg.chat.id, "<blockquote>⚠️ <b>[ FORMAT ERROR ]</b>\n\nGunakan format:\n/delprem ID_USER</blockquote>", { parse_mode: 'HTML' });
    }

    let premiumUsers = loadJsonFile(PREMIUM_FILE);
    let updatedPrems = premiumUsers.filter(item => {
        const idVal = typeof item === 'string' ? item : item.id;
        return idVal !== targetId;
    });

    if (updatedPrems.length === premiumUsers.length) {
        return bot.sendMessage(msg.chat.id, `<blockquote>⚠️ <b>[ DATABASE NOTICE ]</b>\n\nUser ID ${targetId} tidak ditemukan di database premium.</blockquote>`, { parse_mode: 'HTML' });
    }

    saveJsonFile(PREMIUM_FILE, updatedPrems);
    bot.sendMessage(msg.chat.id, `<blockquote>✅ <b>[ OPERATION SUCCESS ]</b>\n\n<b>Berhasil!</b>\nUser ID ${targetId} telah dihapus dari akses Premium.</blockquote>`, { parse_mode: 'HTML' });
});

bot.onText(/\/bc(?: (.+))?/, async (msg, match) => {
    if (msg.chat.type !== 'private') return;
    const userId = msg.from.id;
    const username = msg.from.username;

    if (!checkIsAdmin(userId, username)) return;

    let textToBc = match[1];
    if (!textToBc && msg.reply_to_message) {
        textToBc = msg.reply_to_message.text || msg.reply_to_message.caption;
    }

    if (!textToBc) {
        return bot.sendMessage(msg.chat.id, "<blockquote>⚠️ <b>[ FORMAT ERROR ]</b>\n\nGunakan format:\n/bc [pesan] atau reply pesan dengan /bc</blockquote>", { parse_mode: 'HTML' });
    }

    const users = loadJsonFile(USERS_FILE);
    const statusMsg = await bot.sendMessage(msg.chat.id, `<blockquote>📢 <b>[ BROADCAST STATUS ]</b>\n\nMemulai Broadcast ke ${users.length} users...</blockquote>`, { parse_mode: 'HTML' });

    let success = 0;
    for (const u of users) {
        const uid = typeof u === 'string' ? u : u.id;
        try {
            await bot.sendMessage(uid, `<blockquote>📢 <b>[ BROADCAST INFORMASI DARI DEV ]</b>\n\n` + textToBc + `</blockquote>`, { parse_mode: 'HTML' });
            success++;
            await new Promise(r => setTimeout(r, 200));
        } catch (e) {}
    }

    bot.editMessageText(`<blockquote>✅ <b>[ BROADCAST COMPLETE ]</b>\n\n<b>Broadcast Selesai!</b>\n\n• Berhasil terkirim ke: <b>${success}</b> users.</blockquote>`, {
        chat_id: msg.chat.id,
        message_id: statusMsg.message_id,
        parse_mode: 'HTML'
    }).catch(() => {});
});


// ==========================================
// --- FITUR /SHAREMSG SEKALI KIRIM ---
// ==========================================
let isSharingActive = false;

bot.onText(/\/sharemsg/, async (msg) => {
    if (msg.chat.type !== 'private') return;
    const chatId = msg.chat.id;
    const userId = msg.from.id;
    const username = msg.from.username;
    
    const isJoined = await checkSubscription(userId);
    if (!isJoined) {
        return bot.sendMessage(chatId, `<blockquote>❌ <b>[ ACCESS CONTROL ]</b>\n\n<b>Akses Ditolak!</b>\n\nKamu harus join channel ${REQUIRED_CHANNEL} terlebih dahulu untuk menggunakan perintah ini.</blockquote>`, { parse_mode: 'HTML' });
    }

    if (isSharingActive) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ SYSTEM BUSY ]</b>\n\n<b>Proses Lain Sedang Berjalan!</b>\n\nMohon tunggu sebentar sampai penyebaran pesan sebelumnya selesai.</blockquote>", { parse_mode: 'HTML' });
    }

    const targetMessage = msg.reply_to_message;

    if (!checkIsPremium(userId, username)) {
        return bot.sendMessage(chatId, "<blockquote>❌ <b>[ PREMIUM REQUIRED ]</b>\n\n<b>Akses Ditolak!</b>\n\nKamu belum memiliki akses Premium untuk menggunakan fitur ini, gunakan button syarat akses free lalu ikuti caranya, maka kamu otomatis jadi premium</blockquote>", { parse_mode: 'HTML' });
    }

    if (!targetMessage) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ FORMAT ERROR ]</b>\n\n<b>Format Salah!</b>\n\nSilakan <b>Reply (Balas)</b> pesan atau kutipan yang ingin disebar, lalu ketik /sharemsg</blockquote>", { parse_mode: 'HTML' });
    }

    let registeredGroups = loadJsonFile(GROUPS_FILE);

    if (registeredGroups.length === 0) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ DATABASE EMPTY ]</b>\n\n<b>Database Kosong!</b>\n\nBelum ada group terdaftar di sistem bot.</blockquote>", { parse_mode: 'HTML' });
    }

    isSharingActive = true;
    const statusMsg = await bot.sendMessage(chatId, `<blockquote>📤 <b>[ SHAREMETRICS ]</b>\n\nSedang menyebarkan pesan ke ${registeredGroups.length} group...</blockquote>`, { parse_mode: 'HTML' });

    let successCount = 0;
    for (const g of registeredGroups) {
        const groupId = typeof g === 'string' ? g : g.id;
        try {
            await bot.copyMessage(groupId, chatId, targetMessage.message_id);
            successCount++;
            await new Promise(resolve => setTimeout(resolve, 500));
        } catch (err) {}
    }

    isSharingActive = false;
    bot.editMessageText(`<blockquote>✨ <b>[ SHARE COMPLETE ]</b>\n\n<b>SHAREMSG BERHASIL DISEBARKAN!</b>\n\n• Berhasil disalin ke: <b>${successCount}</b> dari ${registeredGroups.length} group.</blockquote>`, {
        chat_id: chatId,
        message_id: statusMsg.message_id,
        parse_mode: 'HTML'
    }).catch(() => {});
});


// ==========================================
// --- FITUR /AUTOSHAREMSG ---
// ==========================================
bot.onText(/\/autosharemsg/, async (msg) => {
    if (msg.chat.type !== 'private') return;
    const chatId = msg.chat.id;
    const userId = msg.from.id;
    const username = msg.from.username;

    const isJoined = await checkSubscription(userId);
    if (!isJoined) {
        return bot.sendMessage(chatId, `<blockquote>❌ <b>[ ACCESS CONTROL ]</b>\n\n<b>Akses Ditolak!</b>\n\nKamu harus join channel ${REQUIRED_CHANNEL} terlebih dahulu.</blockquote>`, { parse_mode: 'HTML' });
    }

    if (!checkIsPremium(userId, username)) {
        return bot.sendMessage(chatId, "<blockquote>❌ <b>[ PREMIUM REQUIRED ]</b>\n\n<b>Akses Ditolak!</b>\n\nFitur /autosharemsg khusus untuk pengguna berstatus <b>Premium</b>, gunakan button syarat akses free untuk mendapatkan premium.</blockquote>", { parse_mode: 'HTML' });
    }

    if (activeAutoShares.has(userId)) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ SESSION ACTIVE ]</b>\n\n<b>Auto Share Sudah Aktif!</b>\n\nSesi /autosharemsg kamu sedang berjalan. Ketik /stop untuk menghentikannya terlebih dahulu.</blockquote>", { parse_mode: 'HTML' });
    }

    const targetMessage = msg.reply_to_message;
    if (!targetMessage) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ FORMAT ERROR ]</b>\n\n<b>Format Salah!</b>\n\nSilakan <b>Reply (Balas)</b> pesan yang ingin di-looping otomatis, lalu ketik /autosharemsg</blockquote>", { parse_mode: 'HTML' });
    }

    let registeredGroups = loadJsonFile(GROUPS_FILE);
    if (registeredGroups.length === 0) {
        return bot.sendMessage(chatId, "<blockquote>⚠️ <b>[ DATABASE EMPTY ]</b>\n\n<b>Database Kosong!</b>\n\nBelum ada group terdaftar.</blockquote>", { parse_mode: 'HTML' });
    }

    bot.sendMessage(chatId, `<blockquote>🚀 <b>[ AUTOSHARE SYSTEM ]</b>\n\n<b>AUTOSHAREMSG BERHASIL DIAKTIFKAN!</b>\n\n• Bot akan otomatis mengirim pesan ini ke seluruh group secara berulang-ulang.\n• Ketik /stop kapan saja untuk menghentikannya.</blockquote>`, { parse_mode: 'HTML' });

    const INTERVAL_TIME = 60 * 1000; 

    const intervalId = setInterval(async () => {
        let groups = loadJsonFile(GROUPS_FILE);
        for (const g of groups) {
            const groupId = typeof g === 'string' ? g : g.id;
            try {
                await bot.copyMessage(groupId, chatId, targetMessage.message_id);
                await new Promise(resolve => setTimeout(resolve, 500));
            } catch (err) {}
        }
    }, INTERVAL_TIME);

    activeAutoShares.set(userId, intervalId);
});

bot.onText(/\/stop/, (msg) => {
    if (msg.chat.type !== 'private') return;
    const userId = msg.from.id;

    if (activeAutoShares.has(userId)) {
        clearInterval(activeAutoShares.get(userId));
        activeAutoShares.delete(userId);
        return bot.sendMessage(msg.chat.id, "<blockquote>🛑 <b>[ SESSION STOPPED ]</b>\n\n<b>BERHASIL DIHENTIKAN!</b>\n\nSesi /autosharemsg kamu telah dimatikan.</blockquote>", { parse_mode: 'HTML' });
    } else {
        return bot.sendMessage(msg.chat.id, "<blockquote>⚠️ <b>[ SESSION INFO ]</b>\n\nKamu tidak sedang menjalankan sesi /autosharemsg yang aktif.</blockquote>", { parse_mode: 'HTML' });
    }
});


// ==========================================
// --- HANDLE TOMBOL INLINE (CALLBACK QUERY) ---
// ==========================================
bot.on('callback_query', async (callbackQuery) => {
    const msg = callbackQuery.message;
    const data = callbackQuery.data;
    const userId = callbackQuery.from.id;
    const username = callbackQuery.from.username;
    const chatId = msg.chat.id;
    const messageId = msg.message_id;
    const isAdmin = checkIsAdmin(userId, username);

    if (data === 'check_sub_status') {
        const isJoined = await checkSubscription(userId);
        if (isJoined) {
            bot.answerCallbackQuery(callbackQuery.id, { text: '🎉 Verifikasi Berhasil! Selamat datang.', show_alert: true });
            try { bot.deleteMessage(chatId, messageId); } catch (e) {}
            sendMainMenuDisplay(chatId, userId, username);
        } else {
            bot.answerCallbackQuery(callbackQuery.id, { text: '❌ Kamu belum bergabung ke channel @iwawnih!', show_alert: true });
        }
        return;
    }

    if (data === 'menu_jashare') {
        const text = 
            `<blockquote>⚡ <b>[ JASHARE MENU ]</b>\n` +
            `───────────────────────\n` +
            `Fitur utama untuk menyebarkan pesan massal.\n\n` +
            `📌 <b>Daftar Perintah:</b>\n` +
            `• /sharemsg (Kirim sekali ke semua group)\n` +
            `• /autosharemsg (Kirim otomatis terus-menerus)\n` +
            `• /stop (Menghentikan auto share)\n\n` +
            `<i>Pastikan status akun Anda sudah Premium.</i></blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    } 
    else if (data === 'menu_admin') {
        if (!isAdmin) {
            return bot.answerCallbackQuery(callbackQuery.id, { text: '❌ Menu ini khusus Admin!', show_alert: true });
        }

        const text = 
            `<blockquote>👑 <b>[ ADMIN PANEL ]</b>\n` +
            `───────────────────────\n` +
            `Pilih menu manajemen kontrol database di bawah ini:</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: adminMenuKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data.startsWith('admin_listgroup_')) {
        if (!isAdmin) return;
        const page = parseInt(data.split('_')[2]) || 1;
        let groups = loadJsonFile(GROUPS_FILE);
        const perPage = 4; 
        const maxPage = Math.ceil(groups.length / perPage) || 1;
        const validPage = Math.max(1, Math.min(page, maxPage));

        const startIdx = (validPage - 1) * perPage;
        const sliceGroups = groups.slice(startIdx, startIdx + perPage);

        let text = `<blockquote>📋 <b>[ REGISTERED GROUPS ]</b>\n` +
                   `───────────────────────\n` +
                   `Total (${groups.length}) | Page ${validPage}/${maxPage}\n\n`;
        if (groups.length === 0) {
            text += `Belum ada group terdaftar.`;
        } else {
            sliceGroups.forEach((g, index) => {
                const gId = typeof g === 'string' ? g : g.id;
                let gTitle = (typeof g === 'object' && g.title) ? g.title : 'Unknown Group';
                let gUsn = (typeof g === 'object' && g.username) ? g.username : '-';
                
                if (typeof g === 'string') {
                    gTitle = 'Unknown Group';
                    gUsn = '-';
                }

                text += `${startIdx + index + 1}. <b>${escapeHTML(gTitle)}</b>\n   └ Usn/ID: ${escapeHTML(gUsn)} (${gId})\n\n`;
            });
        }
        text += `</blockquote>`;

        let navButtons = [];
        if (validPage > 1) {
            navButtons.push({ text: '⬅️ Prev', callback_data: `admin_listgroup_${validPage - 1}` });
        }
        navButtons.push({ text: `📄 ${validPage}/${maxPage}`, callback_data: 'noop' });
        if (validPage < maxPage) {
            navButtons.push({ text: 'Next ➡️', callback_data: `admin_listgroup_${validPage + 1}` });
        }

        const inlineKeyboard = [
            navButtons,
            [{ text: '🔙 𝑩𝒂𝒄𝒌 𝑻𝒐 𝑨𝒅𝒎𝒊𝒏 𝑷𝒂𝒏𝒆𝒍', callback_data: 'menu_admin' }]
        ];

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: { inline_keyboard: inlineKeyboard },
            parse_mode: 'HTML',
            disable_web_page_preview: true
        }).catch(() => {});
    }
    else if (data.startsWith('admin_listprem_')) {
        if (!isAdmin) return;
        const page = parseInt(data.split('_')[2]) || 1;
        const prems = loadJsonFile(PREMIUM_FILE);
        const perPage = 8;
        const maxPage = Math.ceil(prems.length / perPage) || 1;
        const validPage = Math.max(1, Math.min(page, maxPage));

        const startIdx = (validPage - 1) * perPage;
        const slicePrems = prems.slice(startIdx, startIdx + perPage);

        let text = `<blockquote>💎 <b>[ PREMIUM USERS ]</b>\n` +
                   `───────────────────────\n` +
                   `Total (${prems.length}) | Page ${validPage}/${maxPage}\n\n`;
        if (prems.length === 0) {
            text += `Belum ada user premium tersimpan di file.`;
        } else {
            slicePrems.forEach((p, index) => {
                const pId = typeof p === 'string' ? p : p.id;
                text += `${startIdx + index + 1}. ID: ${pId}\n`;
            });
        }
        text += `</blockquote>`;

        let navButtons = [];
        if (validPage > 1) {
            navButtons.push({ text: '⬅️ Prev', callback_data: `admin_listprem_${validPage - 1}` });
        }
        navButtons.push({ text: `📄 ${validPage}/${maxPage}`, callback_data: 'noop' });
        if (validPage < maxPage) {
            navButtons.push({ text: 'Next ➡️', callback_data: `admin_listprem_${validPage + 1}` });
        }

        const inlineKeyboard = [
            navButtons,
            [{ text: '🔙 𝑩𝒂𝒄𝒌 𝑻𝒐 𝑨𝒅𝒎𝒊𝒏 𝑷𝒂𝒏𝒆𝒍', callback_data: 'menu_admin' }]
        ];

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: { inline_keyboard: inlineKeyboard },
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data === 'admin_howto_add') {
        if (!isAdmin) return;
        const text = 
            `<blockquote>➕ <b>[ GUIDE ADD PREMIUM ]</b>\n` +
            `───────────────────────\n` +
            `Ketik perintah ini di chat pribadi bot:\n` +
            `/addprem ID_USER\n\n` +
            `Contoh: /addprem 7903965113</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backAdminKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data === 'admin_howto_del') {
        if (!isAdmin) return;
        const text = 
            `<blockquote>➖ <b>[ GUIDE DEL PREMIUM ]</b>\n` +
            `───────────────────────\n` +
            `Ketik perintah ini di chat pribadi bot:\n` +
            `/delprem ID_USER\n\n` +
            `Contoh: /delprem 7903965113</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backAdminKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data === 'admin_howto_bc') {
        if (!isAdmin) return;
        const text = 
            `<blockquote>📢 <b>[ GUIDE BROADCAST ]</b>\n` +
            `───────────────────────\n` +
            `Kirim pesan massal ke seluruh user bot dengan mengetik:\n` +
            `/bc [pesan]\n\n` +
            `Atau <b>Reply</b> pesan teks/gambar lalu ketik /bc</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backAdminKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data === 'admin_backup') {
        if (!isAdmin) return;
        try {
            await bot.answerCallbackQuery(callbackQuery.id, { text: '📦 Mengirim file backup database...' });
            if (fs.existsSync(GROUPS_FILE)) await bot.sendDocument(chatId, GROUPS_FILE, { caption: '📦 Backup File: groups.json' });
            if (fs.existsSync(PREMIUM_FILE)) await bot.sendDocument(chatId, PREMIUM_FILE, { caption: '📦 Backup File: premium.json' });
            if (fs.existsSync(USERS_FILE)) await bot.sendDocument(chatId, USERS_FILE, { caption: '📦 Backup File: users.json' });
        } catch (err) {
            bot.sendMessage(chatId, "<blockquote>❌ <b>[ BACKUP ERROR ]</b>\n\nGagal mengirim file backup.</blockquote>", { parse_mode: 'HTML' });
        }
    }
    else if (data === 'menu_buy') {
        const text = 
            `<blockquote>💎 <b>[ BUY ACCESS ]</b>\n` +
            `───────────────────────\n` +
            `Nikmati kebebasan menyebar pesan tanpa batasan.\n\n` +
            `📌 <b>List Harga:</b>\n` +
            `• <b>Harga:</b> Rp 1.000\n` +
            `• <b>Benefit:</b> Akses penuh fitur /sharemsg & /autosharemsg\n\n` +
            `💳 <b>Metode Pembayaran:</b> QRIS / DANA\n` +
            `📌 <b>Order Hubungi:</b> @${OWNER_USERNAME}</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    } 
    else if (data === 'menu_free') {
        const isUserPrem = checkIsPremium(userId, username);
        const statusPremText = isUserPrem ? "✅ Aktif" : "❌ Belum Aktif";
        const registeredGroups = loadJsonFile(GROUPS_FILE);

        const text = 
            `<blockquote>📂 <b>[ FREE ACCESS SYARAT ]</b>\n` +
            `───────────────────────\n` +
            `Dapatkan akses premium secara otomatis tanpa biaya dengan cara:\n\n` +
            `1️⃣ Klik tombol <b>"➕ MASUKKAN BOT KE GROUP"</b>\n` +
            `2️⃣ Masukkan bot ke minimal <b>1 group</b> Telegram\n` +
            `3️⃣ Klik <b>"🔄 Verifikasi / Cek Status"</b> di bawah untuk mengaktifkan akses Premium!\n\n` +
            `📌 <b>Status Akun Anda:</b>\n` +
            `• Total Group Bot: ${registeredGroups.length} group\n` +
            `• Status Premium: ${statusPremText}</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: freeAccessKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    } 
    else if (data === 'check_group_status') {
        let registeredGroups = loadJsonFile(GROUPS_FILE);
        let premiumUsers = loadJsonFile(PREMIUM_FILE);
        let premListStr = premiumUsers.map(p => (typeof p === 'string' ? p : p.id));

        let foundValidGroup = false;
        for (const g of registeredGroups) {
            if (typeof g === 'object' && g.inviter_id && g.inviter_id === userId.toString()) {
                try {
                    const member = await bot.getChatMember(g.id, bot.token.split(':')[0]);
                    if (member && ['administrator', 'member'].includes(member.status)) {
                        foundValidGroup = true;
                        break;
                    }
                } catch (e) {}
            }
        }

        if (foundValidGroup && !premListStr.includes(userId.toString())) {
            premiumUsers.push(userId.toString());
            saveJsonFile(PREMIUM_FILE, premiumUsers);

            let userUsn = username ? `@${escapeHTML(username)}` : 'tidak ada';
            let formattedUser = `${escapeHTML(callbackQuery.from.first_name)}`;
            if (username) {
                formattedUser = `<a href="https://t.me/${username}">${escapeHTML(callbackQuery.from.first_name)} (${userUsn})</a>`;
            }

            const logVerifyPrem = 
                `<blockquote>💎 <b>[ PREMIUM METRICS ]</b>\n` +
                `───────────────────────\n` +
                `💎 <b>NEW PREMIUM USER (VERIFY BUTTON)</b>\n\n` +
                `• User    : ${formattedUser}\n` +
                `• User ID : ${userId}\n` +
                `• Status  : Berhasil verifikasi syarat group dan otomatis jadi Premium!</blockquote>`;
            
            bot.sendMessage(REQUIRED_CHANNEL, logVerifyPrem, { parse_mode: 'HTML', disable_web_page_preview: true }).catch(() => {});
            
            const successUserText = 
                `<blockquote>🎉 <b>[ SELAMAT! AKSES PREMIUM AKTIF ]</b>\n` +
                `───────────────────────\n` +
                `Halo <b>${escapeHTML(callbackQuery.from.first_name)}</b>!\n\n` +
                `Verifikasi berhasil! Sistem mendeteksi kamu telah memasukkan bot ke dalam grup. Status akunmu sekarang resmi menjadi <b>PREMIUM</b> ✨\n\n` +
                `Silakan gunakan fitur /sharemsg atau /autosharemsg sekarang!</blockquote>`;
            bot.sendMessage(userId, successUserText, { parse_mode: 'HTML' }).catch(() => {});
        }

        const isUserPrem = checkIsPremium(userId, username);
        if (isUserPrem) {
            bot.answerCallbackQuery(callbackQuery.id, {
                text: '🎉 Verifikasi Berhasil! Status kamu sekarang Premium!',
                show_alert: true
            });
        } else {
            bot.answerCallbackQuery(callbackQuery.id, {
                text: '❌ Belum terdeteksi! Pastikan kamu yang memasukkan bot ke dalam group.',
                show_alert: true
            });
        }

        const statusPremText = isUserPrem ? "✅ Aktif" : "❌ Belum Aktif";
        const text = 
            `<blockquote>📂 <b>[ FREE ACCESS SYARAT ]</b>\n` +
            `───────────────────────\n` +
            `Status verifikasi berhasil diperbarui!\n\n` +
            `📌 <b>Status Akun Anda:</b>\n` +
            `• Total Group Bot: ${registeredGroups.length} group\n` +
            `• Status Premium: ${statusPremText}</blockquote>`;

        bot.editMessageCaption(text, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: freeAccessKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {});
    }
    else if (data === 'menu_cekid') {
        const firstName = escapeHTML(callbackQuery.from.first_name || 'Tidak ada');
        const usernameTag = username ? `@${escapeHTML(username)}` : 'Tidak ada';
        const isUserPrem = checkIsPremium(userId, username);
        const statusUser = isUserPrem ? "✅ Premium User" : "❌ Free User";

        const infoText = 
            `<blockquote>👤 <b>[ USER INFORMATION ]</b>\n` +
            `───────────────────────\n` +
            `• 🆔 ID    : ${userId}\n` +
            `• 📛 Nama  : ${firstName}\n` +
            `• 🎭 Usn   : ${usernameTag}\n` +
            `• 💎 Status: ${statusUser}</blockquote>`;

        bot.editMessageCaption(infoText, {
            chat_id: chatId,
            message_id: messageId,
            reply_markup: backKeyboard.reply_markup,
            parse_mode: 'HTML'
        }).catch(() => {
            bot.editMessageText(infoText, {
                chat_id: chatId,
                message_id: messageId,
                reply_markup: backKeyboard.reply_markup,
                parse_mode: 'HTML'
            }).catch(() => {
                bot.sendMessage(chatId, infoText, {
                    reply_markup: backKeyboard.reply_markup,
                    parse_mode: 'HTML'
                });
            });
        });
    } 
    else if (data === 'menu_back') {
        sendMainMenuDisplay(chatId, userId, username, messageId);
    }

    bot.answerCallbackQuery(callbackQuery.id).catch(() => {});
});

console.log('Bot Berjalan Sempurna dengan Sinkronisasi Auto-Remove & Auto Un-Prem Group...');