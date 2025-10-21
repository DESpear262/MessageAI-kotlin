import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions';

admin.initializeApp();

// Send push notification on new message
export const sendPushNotification = functions.firestore
  .document('chats/{chatId}/messages/{messageId}')
  .onCreate(async (snapshot, context) => {
    const message = snapshot.data();
    const chatId = context.params.chatId;
    
    // Get chat document to find recipients
    const chatDoc = await admin.firestore()
      .collection('chats')
      .doc(chatId)
      .get();
    
    if (!chatDoc.exists) return;
    
    const chatData = chatDoc.data();
    const participants = chatData?.participants || [];
    const senderId = message.senderId;
    
    // Get recipient FCM tokens (exclude sender)
    const recipients = participants.filter((id: string) => id !== senderId);
    
    if (recipients.length === 0) return;
    
    // Get sender info
    const senderDoc = await admin.firestore()
      .collection('users')
      .doc(senderId)
      .get();
    
    const senderName = senderDoc.data()?.displayName || 'Someone';
    
    // Get FCM tokens
    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore()
        .collection('users')
        .doc(recipientId)
        .get();
      
      const fcmToken = userDoc.data()?.fcmToken;
      if (fcmToken) tokens.push(fcmToken);
    }
    
    if (tokens.length === 0) return;
    
    // Send notification
    const payload = {
      notification: {
        title: senderName,
        body: message.text || '📷 Image'
      },
      data: {
        chatId: chatId,
        messageId: context.params.messageId,
        type: 'message'
      }
    };
    
    await admin.messaging().sendToDevice(tokens, payload);
  });

// Similar function for group messages
export const sendGroupPushNotification = functions.firestore
  .document('groups/{groupId}/messages/{messageId}')
  .onCreate(async (snapshot, context) => {
    const message = snapshot.data();
    const groupId = context.params.groupId;
    
    const groupDoc = await admin.firestore()
      .collection('groups')
      .doc(groupId)
      .get();
    
    if (!groupDoc.exists) return;
    
    const groupData = groupDoc.data();
    const members = groupData?.members || [];
    const senderId = message.senderId;
    
    const recipients = members.filter((id: string) => id !== senderId);
    
    if (recipients.length === 0) return;
    
    const senderDoc = await admin.firestore()
      .collection('users')
      .doc(senderId)
      .get();
    
    const senderName = senderDoc.data()?.displayName || 'Someone';
    const groupName = groupData?.name || 'Group';
    
    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore()
        .collection('users')
        .doc(recipientId)
        .get();
      
      const fcmToken = userDoc.data()?.fcmToken;
      if (fcmToken) tokens.push(fcmToken);
    }
    
    if (tokens.length === 0) return;
    
    const payload = {
      notification: {
        title: `${senderName} in ${groupName}`,
        body: message.text || '📷 Image'
      },
      data: {
        groupId: groupId,
        messageId: context.params.messageId,
        type: 'group_message'
      }
    };
    
    await admin.messaging().sendToDevice(tokens, payload);
  });

// Update user presence on disconnect
export const updatePresenceOnDisconnect = functions.auth
  .user()
  .onDelete(async (user) => {
    await admin.firestore()
      .collection('users')
      .doc(user.uid)
      .update({
        isOnline: false,
        lastSeen: admin.firestore.FieldValue.serverTimestamp()
      });
  });