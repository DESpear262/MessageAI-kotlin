/**
 * MessageAI – Cloud Functions.
 *
 * Contains Firestore-triggered functions that send FCM push notifications for
 * new messages (direct and group) and updates presence on account deletion.
 */
import * as admin from 'firebase-admin';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { user, UserRecord } from 'firebase-functions/v1/auth';

admin.initializeApp();

/** Send push notification on new direct message. */
export const sendPushNotification = onDocumentCreated(
  'chats/{chatId}/messages/{messageId}',
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() as any;
    const chatId = event.params.chatId as string;

    // Get chat document to find recipients
    const chatDoc = await admin.firestore().collection('chats').doc(chatId).get();
    if (!chatDoc.exists) return;

    const chatData = chatDoc.data() as any;
    const participants: string[] = chatData?.participants || [];
    const senderId: string = message.senderId;

    // Get recipient FCM tokens (exclude sender)
    const recipients = participants.filter((id) => id !== senderId);
    if (recipients.length === 0) return;

    // Get sender info
    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';

    // Get FCM tokens
    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) tokens.push(fcmToken);
    }
    if (tokens.length === 0) return;

    // Send notification
    const payload = {
      notification: {
        title: senderName,
        body: message.text || '📷 Image',
      },
      data: {
        chatId,
        messageId: event.params.messageId as string,
        type: 'message',
      },
    };
    await admin.messaging().sendToDevice(tokens, payload as any);
  }
);

/** Send push notification on new group message. */
export const sendGroupPushNotification = onDocumentCreated(
  'groups/{groupId}/messages/{messageId}',
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() as any;
    const groupId = event.params.groupId as string;

    const groupDoc = await admin.firestore().collection('groups').doc(groupId).get();
    if (!groupDoc.exists) return;

    const groupData = groupDoc.data() as any;
    const members: string[] = groupData?.members || [];
    const senderId: string = message.senderId;

    const recipients = members.filter((id) => id !== senderId);
    if (recipients.length === 0) return;

    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';
    const groupName = groupData?.name || 'Group';

    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) tokens.push(fcmToken);
    }
    if (tokens.length === 0) return;

    const payload = {
      notification: {
        title: `${senderName} in ${groupName}`,
        body: message.text || '📷 Image',
      },
      data: {
        groupId,
        messageId: event.params.messageId as string,
        type: 'group_message',
      },
    };
    await admin.messaging().sendToDevice(tokens, payload as any);
  }
);

/** Update user presence on auth user deletion. */
export const updatePresenceOnDisconnect = user().onDelete(async (userRecord: UserRecord) => {
  const uid = userRecord.uid;
  await admin.firestore().collection('users').doc(uid).update({
    isOnline: false,
    lastSeen: admin.firestore.FieldValue.serverTimestamp(),
  });
});