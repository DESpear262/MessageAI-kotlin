/**
 * MessageAI – Cloud Functions.
 *
 * Contains Firestore-triggered functions that send FCM push notifications for
 * new messages (direct and group) and updates presence on account deletion.
 */
import * as admin from 'firebase-admin';
// import { user, UserRecord } from 'firebase-functions/v1/auth';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';

admin.initializeApp();

/** Send push notification on new direct message. */
export const sendPushNotification = onDocumentCreated(
  'chats/{chatId}/messages/{messageId}',
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() as any;
    const chatId = event.params.chatId as string;

    console.log(`New message in chat ${chatId}, sending push notification`);

    // Get chat document to find recipients
    const chatDoc = await admin.firestore().collection('chats').doc(chatId).get();
    if (!chatDoc.exists) {
      console.log('Chat document not found');
      return;
    }

    const chatData = chatDoc.data() as any;
    const participants: string[] = chatData?.participants || [];
    const senderId: string = message.senderId;

    // Get recipient FCM tokens (exclude sender)
    const recipients = participants.filter((id) => id !== senderId);
    if (recipients.length === 0) {
      console.log('No recipients found');
      return;
    }

    // Get sender info
    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';

    // Get FCM tokens
    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) {
        console.log(`Found FCM token for recipient ${recipientId}`);
        tokens.push(fcmToken);
      } else {
        console.log(`No FCM token for recipient ${recipientId}`);
      }
    }
    if (tokens.length === 0) {
      console.log('No valid FCM tokens found');
      return;
    }

    // Send notification using v1 API
    const messagePayload = {
      notification: {
        title: senderName,
        body: message.text || '📷 Image',
      },
      data: {
        chatId,
        messageId: event.params.messageId as string,
        type: 'message',
      },
      tokens,
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(messagePayload);
      console.log(`Successfully sent ${response.successCount} notifications, ${response.failureCount} failed`);
      
      // Log any failures
      if (response.failureCount > 0) {
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            console.error(`Failed to send to token ${tokens[idx]}: ${resp.error?.message}`);
          }
        });
      }
    } catch (error) {
      console.error('Error sending push notification:', error);
    }
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

    console.log(`New message in group ${groupId}, sending push notification`);

    const groupDoc = await admin.firestore().collection('groups').doc(groupId).get();
    if (!groupDoc.exists) {
      console.log('Group document not found');
      return;
    }

    const groupData = groupDoc.data() as any;
    const members: string[] = groupData?.members || [];
    const senderId: string = message.senderId;

    const recipients = members.filter((id) => id !== senderId);
    if (recipients.length === 0) {
      console.log('No recipients found');
      return;
    }

    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';
    const groupName = groupData?.name || 'Group';

    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) {
        console.log(`Found FCM token for group member ${recipientId}`);
        tokens.push(fcmToken);
      }
    }
    if (tokens.length === 0) {
      console.log('No valid FCM tokens found');
      return;
    }

    const messagePayload = {
      notification: {
        title: `${senderName} in ${groupName}`,
        body: message.text || '📷 Image',
      },
      data: {
        groupId,
        messageId: event.params.messageId as string,
        type: 'group_message',
      },
      tokens,
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(messagePayload);
      console.log(`Successfully sent ${response.successCount} notifications, ${response.failureCount} failed`);
      
      if (response.failureCount > 0) {
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            console.error(`Failed to send to token ${tokens[idx]}: ${resp.error?.message}`);
          }
        });
      }
    } catch (error) {
      console.error('Error sending group push notification:', error);
    }
  }
);

/** Update user presence on auth user deletion. */
// Temporarily disabled - v1 auth triggers causing deployment issues
// TODO: Migrate to v2 auth triggers or Cloud Scheduler
// export const updatePresenceOnDisconnect = user().onDelete(async (userRecord: UserRecord) => {
//   const uid = userRecord.uid;
//   await admin.firestore().collection('users').doc(uid).update({
//     isOnline: false,
//     lastSeen: admin.firestore.FieldValue.serverTimestamp(),
//   });
// });
